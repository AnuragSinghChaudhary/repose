package features.core.proxy

import framework.ReposeConfigurationProvider
import framework.ReposeLauncher
import framework.ReposeContainerLauncher
import framework.TestProperties
import org.linkedin.util.clock.SystemClock
import org.rackspace.gdeproxy.Deproxy
import org.rackspace.gdeproxy.MessageChain
import org.rackspace.gdeproxy.PortFinder
import spock.lang.Specification

import static org.linkedin.groovy.util.concurrent.GroovyConcurrentUtils.waitForCondition

class TomcatProxyTest extends Specification {

    static ReposeLauncher repose1
    static Deproxy deproxy
    static String tomcatEndpoint

    def setupSpec() {

        PortFinder pf = new PortFinder()

        int originServicePort = pf.getNextOpenPort()
        deproxy = new Deproxy()
        deproxy.addEndpoint(originServicePort)

        int reposePort1 = pf.getNextOpenPort()
        int shutdownPort1 = pf.getNextOpenPort()
        def TestProperties properties = new TestProperties(ClassLoader.getSystemResource("test.properties").openStream())
        tomcatEndpoint = "http://localhost:${reposePort1}"

        def configDirectory = properties.getConfigDirectory()
        def configSamples = properties.getRawConfigDirectory()
        def rootWar = properties.getReposeRootWar()
        def buildDirectory = properties.getReposeHome() + "/.."
        ReposeConfigurationProvider config1 = new ReposeConfigurationProvider(configDirectory, configSamples)

        config1.applyConfigsRuntime("features/core/proxy",
                [
                        'repose_port': reposePort1.toString(),
                        'target_port': originServicePort.toString(),
                        'repose.config.directory': configDirectory,
                        'repose.cluster.id': "repose1",
                        'repose.node.id': 'node1',
                        'target_hostname': 'localhost',
                ]
        )
        config1.applyConfigsRuntime("common", ['project.build.directory': buildDirectory])


        repose1 = new ReposeContainerLauncher(config1, properties.getTomcatJar(), "repose1", "node1", rootWar, reposePort1, shutdownPort1)
        repose1.clusterId = "repose"
        repose1.nodeId = "simple-node"
        repose1.start()
    }

    def cleanupSpec() {
        if (deproxy)
            deproxy.shutdown()

        if (repose1)
            repose1.stop()
    }

    def "Should Pass Requests through repose"() {

        when:
        waitUntilReadyToServiceRequests(tomcatEndpoint)
        MessageChain mc1 = deproxy.makeRequest(url: tomcatEndpoint + "/cluster", headers: ['x-trace-request': 'true', 'x-pp-user': 'usertest1'])

        then:
        mc1.receivedResponse.code == "200"
    }


    def waitUntilReadyToServiceRequests(String reposeEndpoint) {
        def clock = new SystemClock()
        def innerDeproxy = new Deproxy()
        MessageChain mc
        waitForCondition(clock, '60s', '1s', {
            try {
                mc = innerDeproxy.makeRequest([url: reposeEndpoint])
            } catch (Exception e) {}
            if (mc != null) {
                if (mc.receivedResponse.code.equalsIgnoreCase("200")) {
                    return true
                }
            } else {
                return false
            }
        })
    }
}
