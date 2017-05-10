FROM openjdk:8

RUN mkdir /red
WORKDIR /red

COPY ./target/scala-2.12/cerberus.jar cerberus.jar

ADD charon.sv.conf /etc/supervisor/conf.d/

RUN apt-get update && apt-get -y -q install supervisor

CMD ["/usr/bin/supervisord"]