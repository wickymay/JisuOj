FROM docker.m.daocloud.io/library/ubuntu:24.04 AS build
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential libmariadb-dev-compat libmariadb-dev && rm -rf /var/lib/apt/lists/*
COPY trunk/core/judged /src/judged
COPY trunk/core/judge_client /src/judge_client
RUN make -C /src/judged && make -C /src/judge_client

FROM docker.m.daocloud.io/library/ubuntu:24.04
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates coreutils diffutils gcc g++ make \
    libmariadb3 openjdk-17-jdk-headless python3 python3-pip \
    fp-compiler ruby php-cli mono-mcs && rm -rf /var/lib/apt/lists/* \
    && useradd -m -u 1536 judge \
    && mkdir -p /home/judge/etc /home/judge/data /home/judge/log \
    && for id in 0 1 2 3; do mkdir -p "/home/judge/run${id}"; done \
    && chown -R judge:judge /home/judge
COPY --from=build /src/judged/judged /usr/bin/judged
COPY --from=build /src/judge_client/judge_client /usr/bin/judge_client
COPY trunk/install/java0.policy /home/judge/etc/java0.policy
COPY trunk/install/judge.conf /opt/jisuoj/judge.conf.template
COPY jisuoj-next/judge-agent/entrypoint.sh /usr/local/bin/jisuoj-judge-entrypoint
RUN sed -i 's/\r$//' /usr/local/bin/jisuoj-judge-entrypoint \
    && chmod 0755 /usr/bin/judged /usr/bin/judge_client /usr/local/bin/jisuoj-judge-entrypoint
WORKDIR /home/judge
EXPOSE 1536/udp
ENTRYPOINT ["/usr/local/bin/jisuoj-judge-entrypoint"]
