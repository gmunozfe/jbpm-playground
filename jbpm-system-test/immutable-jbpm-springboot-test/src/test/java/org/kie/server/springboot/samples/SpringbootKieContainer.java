package org.kie.server.springboot.samples;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.ToStringConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

public class SpringbootKieContainer extends GenericContainer<SpringbootKieContainer>{

    private static final int KIE_PORT = 8090;
    private static final Logger logger = LoggerFactory.getLogger(SpringbootKieContainer.class);
    
    static {
        // kjar
        executeMaven("./src/test/resources/kjars/test-sample", Arrays.asList("clean","install"));

        // service
        executeMaven(".", Arrays.asList("clean","install", "spring-boot:repackage"));
    }

    private static void executeMaven(String pomPath, List<String> goals) {
        File cwd = new File(pomPath);
        
        Properties properties = new Properties();
        // Avoid recursion
        properties.put("skipTests", "true");

        InvocationRequest request = new DefaultInvocationRequest()
                .setPomFile(new File(cwd, "pom.xml"))
                .setGoals(goals)
                .setInputStream(new ByteArrayInputStream(new byte[0]))
                .setProperties(properties);

        InvocationResult invocationResult = null;
        try {
            invocationResult = new DefaultInvoker().execute(request);
        } catch (MavenInvocationException e) {
            throw new RuntimeException(e);
        }

        if (invocationResult!=null && invocationResult.getExitCode() != 0) {
            throw new RuntimeException(invocationResult.getExecutionException());
        }
    }
    
    public SpringbootKieContainer(String id, String name) {
        super(new ImageFromDockerfile()
                  .withFileFromClasspath("Dockerfile", "etc/Dockerfile")
                  .withFileFromFile("target/immutable-jbpm-springboot-test.jar", new File("target/immutable-jbpm-springboot-test-7.58.0.Final.jar")));
        withExposedPorts(KIE_PORT);
        withLogConsumer(new ToStringConsumer() {
                    @Override
                    public void accept(OutputFrame outputFrame) {
                        String message = outputFrame.getUtf8String().replaceAll("\\r?\\n?$", "");
                        logger.info(message);
                    }
                });
        
        
        withNetwork(Network.SHARED);
        withEnv("SPRING_DATASOURCE_URL", "jdbc:postgresql://db:5432/rhpamdatabase?user=rhpamuser&password=rhpampassword");
        withEnv("kieserver.serverId", id);
        withEnv("kieserver.serverName", name);
        waitingFor(Wait.forLogMessage(".*Started KieServerApplication in.*", 1).withStartupTimeout(Duration.ofMinutes(5L)));
    }
    
    public Integer getKiePort() {
        return this.getMappedPort(KIE_PORT);
    }

}