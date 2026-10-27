FROM golang:1.26.10-alpine@sha256:27f2d3b83cfc5803c8078f8e730af0f7c431bf067afaec8f60a36fb763187cba as build
RUN apk add --no-cache --update git
RUN mkdir -p /go/src/github.com/diegopereiraeng/harness-cie-sonarqube-scanner
WORKDIR /go/src/github.com/diegopereiraeng/harness-cie-sonarqube-scanner 
COPY *.go ./
COPY go.mod go.sum ./

RUN go env GOCACHE 

RUN GOOS=linux GOARCH=amd64 CGO_ENABLED=0 go build -mod=readonly -o harness-sonar

FROM amazoncorretto:17-alpine3.21@sha256:258a5ea1fe5e95a456617cb022c3b52e5dcf0d9cd85533fb2064ee33016ac640
ENV NODE_OPTIONS="--use-openssl-ca"
ENV REQUESTS_CA_BUNDLE=/etc/ssl/certs/ca-certificates.crt PIP_CERT=/etc/ssl/certs/ca-certificates.crt CURL_CA_BUNDLE=/etc/ssl/certs/ca-certificates.crt

ARG SONAR_VERSION=5.0.1.3006
ARG SONAR_SCANNER_CLI=sonar-scanner-cli-${SONAR_VERSION}
ARG SONAR_SCANNER=sonar-scanner-${SONAR_VERSION}

# RUN apt-get update \
#     && apt-get install -y nodejs curl \
#     && apt-get clean

RUN apk --no-cache --update add 'amazon-corretto-17>=17.0.21.0.11-r0' 'ca-certificates>=20261020-r0' 'nodejs>=22.23.4' npm curl unzip git php python3 py3-pip maven gcc g++ make ruby ruby-dev perl \
    && python3 -m pip install --no-cache-dir --break-system-packages 'pip==26.2.2' 'certifi==2026.10.19'

RUN update-ca-certificates

COPY --from=build /go/src/github.com/diegopereiraeng/harness-cie-sonarqube-scanner/harness-sonar /bin/
WORKDIR /bin

RUN curl https://binaries.sonarsource.com/Distribution/sonar-scanner-cli/${SONAR_SCANNER_CLI}.zip -so /bin/${SONAR_SCANNER_CLI}.zip
RUN unzip ${SONAR_SCANNER_CLI}.zip \
    && rm ${SONAR_SCANNER_CLI}.zip 

ENV PATH $PATH:/bin/${SONAR_SCANNER}/bin

ENTRYPOINT /bin/harness-sonar
