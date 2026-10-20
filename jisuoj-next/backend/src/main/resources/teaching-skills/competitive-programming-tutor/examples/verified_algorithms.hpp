#pragma once

// Independent C++17 teaching implementations, not verbatim book code.
// Integer optima and finite path lengths must fit the stated ranges.
#include <algorithm>
#include <limits>
#include <numeric>
#include <queue>
#include <stdexcept>
#include <string>
#include <utility>
#include <vector>

namespace jisuoj_teaching {

inline int lower_index(const std::vector<int>& sorted, int target) {
    int lo = 0, hi = static_cast<int>(sorted.size());
    while (lo < hi) {
        int mid = lo + (hi - lo) / 2;
        if (sorted[mid] < target) lo = mid + 1;
        else hi = mid;
    }
    return lo;
}

// Each item is (positive volume, value); empty selection is permitted.
inline long long knapsack01(const std::vector<std::pair<int, long long>>& items, int capacity) {
    if (capacity < 0) throw std::invalid_argument("negative capacity");
    std::vector<long long> best(capacity + 1, 0);
    for (auto [volume, value] : items) {
        if (volume <= 0) throw std::invalid_argument("nonpositive volume");
        for (int c = capacity; c >= volume; --c)
            best[c] = std::max(best[c], best[c - volume] + value);
    }
    return best[capacity];
}

inline std::vector<int> unit_distances(const std::vector<std::vector<int>>& graph, int source) {
    std::vector<int> distance(graph.size(), -1);
    std::queue<int> pending;
    distance.at(source) = 0;
    pending.push(source);
    while (!pending.empty()) {
        int vertex = pending.front();
        pending.pop();
        for (int next : graph.at(vertex)) {
            if (distance.at(next) != -1) continue;
            distance[next] = distance[vertex] + 1;
            pending.push(next);
        }
    }
    return distance;
}

class DisjointSet {
    std::vector<int> parent, size;
public:
    explicit DisjointSet(int count) {
        if (count < 0) throw std::invalid_argument("negative size");
        parent.resize(count);
        size.assign(count, 1);
        std::iota(parent.begin(), parent.end(), 0);
    }
    int find(int vertex) {
        int root = vertex;
        while (parent.at(root) != root) root = parent[root];
        while (vertex != root) {
            int previous = parent[vertex];
            parent[vertex] = root;
            vertex = previous;
        }
        return root;
    }
    bool unite(int first, int second) {
        int a = find(first), b = find(second);
        if (a == b) return false;
        if (size[a] < size[b]) std::swap(a, b);
        parent[b] = a;
        size[a] += size[b];
        return true;
    }
};

inline constexpr long long unreachable = std::numeric_limits<long long>::max() / 4;
// Weights are in [0, unreachable); supported finite distances are below unreachable.
inline std::vector<long long> nonnegative_distances(
        const std::vector<std::vector<std::pair<int, long long>>>& graph, int source) {
    for (const auto& edges : graph)
        for (auto [next, weight] : edges) {
            if (next < 0 || next >= static_cast<int>(graph.size()) || weight < 0 || weight >= unreachable)
                throw std::invalid_argument("invalid edge");
        }
    std::vector<long long> distance(graph.size(), unreachable);
    using Entry = std::pair<long long, int>;
    std::priority_queue<Entry, std::vector<Entry>, std::greater<Entry>> pending;
    distance.at(source) = 0;
    pending.push({0, source});
    while (!pending.empty()) {
        auto [current, vertex] = pending.top();
        pending.pop();
        if (current != distance[vertex]) continue;
        for (auto [next, weight] : graph[vertex]) {
            if (current >= unreachable - weight) continue;
            long long candidate = current + weight;
            if (candidate < distance[next]) {
                distance[next] = candidate;
                pending.push({candidate, next});
            }
        }
    }
    return distance;
}

inline std::vector<int> prefix_lengths(const std::string& pattern) {
    std::vector<int> pi(pattern.size());
    for (int i = 1; i < static_cast<int>(pattern.size()); ++i) {
        int j = pi[i - 1];
        while (j > 0 && pattern[i] != pattern[j]) j = pi[j - 1];
        if (pattern[i] == pattern[j]) ++j;
        pi[i] = j;
    }
    return pi;
}

inline std::vector<int> match_positions(const std::string& text, const std::string& pattern) {
    std::vector<int> matches;
    if (pattern.empty()) {
        for (int i = 0; i <= static_cast<int>(text.size()); ++i) matches.push_back(i);
        return matches;
    }
    auto pi = prefix_lengths(pattern);
    int j = 0;
    for (int i = 0; i < static_cast<int>(text.size()); ++i) {
        while (j > 0 && text[i] != pattern[j]) j = pi[j - 1];
        if (text[i] == pattern[j]) ++j;
        if (j == static_cast<int>(pattern.size())) {
            matches.push_back(i + 1 - j);
            j = pi[j - 1];
        }
    }
    return matches;
}

inline long long gcd_nonnegative(long long a, long long b) {
    if (a < 0 || b < 0) throw std::invalid_argument("negative gcd argument");
    while (b != 0) {
        long long remainder = a % b;
        a = b;
        b = remainder;
    }
    return a;
}

// Requires GCC/Clang __int128; modulus is positive and exponent nonnegative.
inline long long power_mod(long long base, unsigned long long exponent, long long modulus) {
    if (modulus <= 0) throw std::invalid_argument("nonpositive modulus");
    base %= modulus;
    if (base < 0) base += modulus;
    long long result = 1 % modulus;
    while (exponent) {
        if (exponent & 1) result = static_cast<__int128>(result) * base % modulus;
        base = static_cast<__int128>(base) * base % modulus;
        exponent >>= 1;
    }
    return result;
}

struct Point { int x, y; };
inline __int128 turn(Point a, Point b, Point c) {
    __int128 x1 = static_cast<__int128>(b.x) - a.x;
    __int128 y1 = static_cast<__int128>(b.y) - a.y;
    __int128 x2 = static_cast<__int128>(c.x) - a.x;
    __int128 y2 = static_cast<__int128>(c.y) - a.y;
    return x1 * y2 - y1 * x2;
}

} // namespace jisuoj_teaching
