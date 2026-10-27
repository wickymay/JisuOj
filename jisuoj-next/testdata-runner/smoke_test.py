"""Opt-in real isolation check after installing rootless Podman and building the image."""
import server

if not server.ready():
    raise SystemExit("Rootless Podman/cgroup v2/toolchain image not ready; see docs/TEST_DATA_GENERATION.md")

solution = '#include <iostream>\nint main(){long long a,b;std::cin>>a>>b;std::cout<<a+b<<"\\n";}'
result = server.generate({
    "caseCount": 2, "seed": 42,
    "generator": '#include <iostream>\n#include <cstdlib>\nint main(int argc,char**argv){std::cout<<std::atoi(argv[1])<<" 4\\n";}',
    "reference": solution, "independent": solution,
    "validator": '#include <iostream>\nint main(){long long a,b;std::string extra;if(!(std::cin>>a>>b)||a<0||b<0||a>100||b>100||std::cin>>extra)return 1;}',
    "mutant": '#include <iostream>\nint main(){int a,b;std::cin>>a>>b;std::cout<<a-b;}',
    "attackInputs": ["0 0\n"], "sampleInput": "1 2\n", "sampleOutput": "3\n",
})
assert result["ok"] and len(result["cases"]) == 4 and result["mutantRejected"] > 0
print(f"Isolation smoke test passed: {len(result['cases'])} cases, {result['mutantRejected']} mutant rejections")
