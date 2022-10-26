# Multi-stage Dockerfile

# First, build kjar from a maven image

ARG IMAGE_NAME
ARG HOSTNAME

FROM maven:3.6.3-openjdk-8-slim as builder
LABEL autodelete="true"
COPY etc/kjars/ /etc/kjars/
RUN mvn --file /etc/kjars/failover-jobs-sample/pom.xml --batch-mode install -DskipTests

#########################################################
# Dockerfile that provides the image for KIE Server
#########################################################

FROM $IMAGE_NAME
ARG HOSTNAME

RUN echo "Building from $IMAGE_NAME"

COPY --from=builder /root/.m2 /opt/jboss/.m2

ENV KIE_SERVER_PROFILE standalone

ENV JAVA_OPTS -Xms256m -Xmx2048m -XX:MetaspaceSize=96M -XX:MaxMetaspaceSize=512m -Djava.net.preferIPv4Stack=true -Dfile.encoding=UTF-8 \
 -Dorg.uberfire.domain=other \
 -Djgroups.bind_addr=$HOSTNAME -Dorg.kie.controller.ping.alive.timeout=5000000 -Dorg.kie.server.controller=http://full-node1:8080/business-central/rest/controller \
 -Dorg.kie.server.controller.user=kieserver -Dorg.kie.server.controller.pwd=kieserver1! -Dorg.jbpm.ejb.timer.local.cache=true -Dorg.jbpm.ejb.timer.tx=true \
 -Dorg.kie.jbpm.timer.retry.limit=1 -Dorg.kie.jbpm.timer.retry.interval=100 -Dorg.kie.executor.pool.size=1000

COPY etc/drivers /etc/drivers/
ADD etc/jbpm-custom.cli $JBOSS_HOME/bin/jbpm-custom.cli

ADD etc/jboss-deployment-structure.xml /WEB-INF/jboss-deployment-structure.xml
ADD etc/kie-server-services-jbpm-cluster-7.67.0.Final.jar /WEB-INF/lib/kie-server-services-jbpm-cluster-7.67.0.Final.jar
ADD etc/kie-server-jms-7.67.1-SNAPSHOT.jar /WEB-INF/lib/kie-server-jms-7.67.0.Final.jar

RUN if [ "$HOSTNAME" == "kie-server-node2" ]; then \
# Replace jboss-deployment-structure.xml for a patched one including modules for infinispan
rm $JBOSS_HOME/standalone/deployments/kie-server.war/WEB-INF/jboss-deployment-structure.xml; \
cp /WEB-INF/jboss-deployment-structure.xml $JBOSS_HOME/standalone/deployments/kie-server.war/WEB-INF/jboss-deployment-structure.xml; \
# Add the jar for the infinispan extension
cp /WEB-INF/lib/kie-server-services-jbpm-cluster-7.67.0.Final.jar $JBOSS_HOME/standalone/deployments/kie-server.war/WEB-INF/lib/kie-server-services-jbpm-cluster-7.67.0.Final.jar; \
rm $JBOSS_HOME/standalone/deployments/kie-server.war/WEB-INF/lib/kie-server-jms-7.67.0.Final.jar; \
cp /WEB-INF/lib/kie-server-jms-7.67.0.Final.jar $JBOSS_HOME/standalone/deployments/kie-server.war/WEB-INF/lib/kie-server-jms-7.67.0.Final.jar; \
fi

RUN if [ "$HOSTNAME" == "full-node1" ]; then \
# Replace jboss-deployment-structure.xml for a patched one including modules for infinispan
jar -uvf $JBOSS_HOME/standalone/deployments/kie-server.war /WEB-INF/jboss-deployment-structure.xml; \
# Add the jar for the infinispan extension
jar -uvf $JBOSS_HOME/standalone/deployments/kie-server.war /WEB-INF/lib/kie-server-services-jbpm-cluster-7.67.0.Final.jar; \
jar -uvf $JBOSS_HOME/standalone/deployments/kie-server.war /WEB-INF/lib/kie-server-jms-7.67.0.Final.jar; \
fi


USER root

RUN chmod +x $JBOSS_HOME/bin/jboss-cli.sh
RUN chown jboss:jboss $JBOSS_HOME/bin/jboss-cli.sh $JBOSS_HOME/bin/jbpm-custom.cli

USER jboss

RUN $JBOSS_HOME/bin/jboss-cli.sh --file=$JBOSS_HOME/bin/jbpm-custom.cli && \
rm -rf $JBOSS_HOME/standalone/configuration/standalone_xml_history/current

WORKDIR $JBOSS_HOME/bin/

#Add a management user for JMX access
RUN $JBOSS_HOME/bin/add-user.sh jmxUser jmxPassword1!

CMD "sh" "-c" "./${START_SCRIPT}"
