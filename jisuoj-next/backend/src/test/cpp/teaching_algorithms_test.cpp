#include "verified_algorithms.hpp"
#include <climits>
#include <iostream>
#include <random>

using namespace jisuoj_teaching;
static int checks = 0;
static void require(bool condition, const char* label) {
    ++checks;
    if (!condition) throw std::runtime_error(label);
}

int main() {
    std::mt19937 random(20261018);
    for (int trial = 0; trial < 500; ++trial) {
        int n = random() % 12;
        std::vector<int> values(n);
        for (int& value : values) value = static_cast<int>(random() % 13) - 6;
        std::sort(values.begin(), values.end());
        for (int target = -8; target <= 8; ++target) {
            int expected = std::lower_bound(values.begin(), values.end(), target) - values.begin();
            require(lower_index(values, target) == expected, "lower bound differs from standard library");
        }

        std::vector<std::pair<int, long long>> items;
        for (int i = 0; i < n; ++i) items.push_back({1 + random() % 7, static_cast<long long>(random() % 14) - 3});
        int capacity = random() % 17;
        long long brute = 0;
        for (int mask = 0; mask < (1 << n); ++mask) {
            int volume = 0;
            long long value = 0;
            for (int i = 0; i < n; ++i) if (mask & (1 << i)) {
                volume += items[i].first;
                value += items[i].second;
            }
            if (volume <= capacity) brute = std::max(brute, value);
        }
        require(knapsack01(items, capacity) == brute, "0/1 knapsack differs from exhaustive subsets");

        std::string text, pattern;
        for (int i = 0; i < 20; ++i) text += static_cast<char>('a' + random() % 3);
        for (int i = 0; i < trial % 8; ++i) pattern += static_cast<char>('a' + random() % 3);
        std::vector<int> naive;
        for (int i = 0; i + pattern.size() <= text.size(); ++i)
            if (text.compare(i, pattern.size(), pattern) == 0) naive.push_back(i);
        require(match_positions(text, pattern) == naive, "KMP differs from naive overlapping matches");
        for (int i = 0; i < static_cast<int>(pattern.size()); ++i) {
            int expected = 0;
            for (int length = 1; length <= i; ++length)
                if (pattern.substr(0, length) == pattern.substr(i + 1 - length, length)) expected = length;
            require(prefix_lengths(pattern)[i] == expected, "prefix length differs from substring enumeration");
        }

        long long a = random() % 100000, b = random() % 100000;
        require(gcd_nonnegative(a, b) == std::gcd(a, b), "gcd differs from standard library");
        long long base = static_cast<int>(random() % 101) - 50, modulus = 1 + random() % 31;
        unsigned exponent = random() % 20;
        long long repeated = 1 % modulus, normalized = (base % modulus + modulus) % modulus;
        for (unsigned i = 0; i < exponent; ++i) repeated = repeated * normalized % modulus;
        require(power_mod(base, exponent, modulus) == repeated, "modular exponent differs from repeated multiplication");
    }
    require(knapsack01({{2, 3}}, 4) == 3, "single item cannot be selected twice");
    require(match_positions("aaaa", "aa") == std::vector<int>({0, 1, 2}), "overlapping matches");
    require(gcd_nonnegative(0, 9) == 9 && gcd_nonnegative(0, 0) == 0, "gcd zero cases");
    require(power_mod(LLONG_MAX - 1, 2, LLONG_MAX) == 1, "multiplication must not overflow before modulus");

    for (int trial = 0; trial < 100; ++trial) {
        constexpr int n = 8;
        std::vector<std::vector<std::pair<int, long long>>> weighted(n);
        std::vector<std::vector<int>> graph(n);
        std::vector<std::vector<long long>> floyd(n, std::vector<long long>(n, unreachable));
        for (int i = 0; i < n; ++i) floyd[i][i] = 0;
        for (int i = 0; i < n; ++i) for (int j = 0; j < n; ++j) if (random() % 4 == 0) {
            int weight = random() % 10;
            weighted[i].push_back({j, weight});
            graph[i].push_back(j);
            floyd[i][j] = std::min(floyd[i][j], static_cast<long long>(weight));
        }
        for (int k = 0; k < n; ++k) for (int i = 0; i < n; ++i) for (int j = 0; j < n; ++j)
            if (floyd[i][k] != unreachable && floyd[k][j] != unreachable)
                floyd[i][j] = std::min(floyd[i][j], floyd[i][k] + floyd[k][j]);
        for (int source = 0; source < n; ++source)
            require(nonnegative_distances(weighted, source) == floyd[source], "Dijkstra differs from Floyd-Warshall");
        auto unit = weighted;
        for (auto& edges : unit) for (auto& edge : edges) edge.second = 1;
        for (int source = 0; source < n; ++source) {
            auto distances = unit_distances(graph, source);
            auto expected = nonnegative_distances(unit, source);
            for (int v = 0; v < n; ++v)
                require((expected[v] == unreachable ? -1 : expected[v]) == distances[v], "BFS unit edge distance");
        }

        DisjointSet sets(n);
        std::vector<int> component(n);
        std::iota(component.begin(), component.end(), 0);
        for (int step = 0; step < 20; ++step) {
            int a = random() % n, b = random() % n;
            bool distinct = component[a] != component[b];
            require(sets.unite(a, b) == distinct, "union result");
            int previous = component[b], replacement = component[a];
            for (int& value : component) if (value == previous) value = replacement;
            for (int i = 0; i < n; ++i) for (int j = 0; j < n; ++j)
                require((sets.find(i) == sets.find(j)) == (component[i] == component[j]), "DSU differs from explicit relabeling");
        }
    }
    bool rejected = false;
    try { nonnegative_distances({{{1, -1}}, {}}, 0); }
    catch (const std::invalid_argument&) { rejected = true; }
    require(rejected, "negative edge rejected");
    require(turn({0, 0}, {3, 0}, {1, 2}) == 6, "left orientation");
    require(turn({0, 0}, {3, 0}, {1, -2}) == -6, "right orientation");
    require(turn({0, 0}, {3, 0}, {6, 0}) == 0, "collinearity");
    require(turn({INT_MIN, INT_MIN}, {INT_MAX, INT_MIN}, {INT_MIN, INT_MAX}) > 0, "wide orientation arithmetic");
    std::cout << "Teaching algorithms passed: " << checks << " checks across binary search, knapsack, BFS, Dijkstra, DSU, KMP, gcd, modular exponentiation and orientation.\n";
}
