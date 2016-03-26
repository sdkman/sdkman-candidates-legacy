FROM marc0der/vertx:1.3.1.final

MAINTAINER Marco Vermeulen

RUN mkdir /sdkman
RUN mkdir /mods

ADD build/server /sdkman
ADD build/templates /sdkman
ADD mods /mods

ENTRYPOINT opt/vert.x-1.3.1.final/bin/vertx run /sdkman/server.groovy

