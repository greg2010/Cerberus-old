FROM openjdk:8-jre-alpine

RUN mkdir /red
WORKDIR /red

COPY ./target/scala-2.12/cerberus.jar cerberus.jar

ADD cerberus.sv.conf /etc/supervisor/conf.d/

RUN apk update
RUN apk add supervisor

CMD ["/usr/bin/supervisord", "-c", "/etc/supervisor/conf.d/cerberus.sv.conf"]