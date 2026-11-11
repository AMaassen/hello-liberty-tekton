# Open Liberty op Red Hat UBI. 'kernel-slim' bevat bijna niets: features.sh installeert
# alleen wat in server.xml staat.
FROM icr.io/appcafe/open-liberty:kernel-slim-java17-openj9-ubi

COPY --chown=1001:0 src/main/liberty/config/ /config/
RUN features.sh

# De WAR wordt eerder in de pipeline gebouwd door de Tekton-task 'maven-build'
COPY --chown=1001:0 target/hello.war /config/apps/
RUN configure.sh

EXPOSE 9080
