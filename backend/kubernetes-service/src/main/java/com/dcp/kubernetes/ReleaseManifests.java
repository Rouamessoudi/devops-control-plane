package com.dcp.kubernetes;

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscalerBuilder;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRoute;
import io.fabric8.kubernetes.api.model.gatewayapi.v1.HTTPRouteBuilder;

import java.util.UUID;

public final class ReleaseManifests {

    static final String MANAGED_LABEL = "app.kubernetes.io/managed-by";
    static final String MANAGED_BY = "devops-control-plane";
    static final String APPLICATION_LABEL = "dcp.application-id";
    static final String TRACK_LABEL = "dcp.track";

    private ReleaseManifests() {
    }

    public static Deployment trackedDeployment(String namespace, String name, String track, String image, int replicas, int containerPort, UUID applicationId) {
        return new DeploymentBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .addToLabels("app", appLabel(name))
                    .addToLabels(TRACK_LABEL, track)
                    .addToLabels(MANAGED_LABEL, MANAGED_BY)
                    .addToLabels(APPLICATION_LABEL, applicationId.toString())
                .endMetadata()
                .withNewSpec()
                    .withReplicas(replicas)
                    .withNewSelector()
                        .addToMatchLabels("app", appLabel(name))
                        .addToMatchLabels(TRACK_LABEL, track)
                    .endSelector()
                    .withNewTemplate()
                        .withNewMetadata()
                            .addToLabels("app", appLabel(name))
                            .addToLabels(TRACK_LABEL, track)
                            .addToLabels(MANAGED_LABEL, MANAGED_BY)
                            .addToLabels(APPLICATION_LABEL, applicationId.toString())
                        .endMetadata()
                        .withNewSpec()
                            .addNewContainer()
                                .withName("app")
                                .withImage(image)
                                .withImagePullPolicy("IfNotPresent")
                                .addNewPort().withContainerPort(containerPort).endPort()
                                .withNewReadinessProbe()
                                    .withNewTcpSocket().withPort(new IntOrString(containerPort)).endTcpSocket()
                                .endReadinessProbe()
                                .withNewLivenessProbe()
                                    .withNewTcpSocket().withPort(new IntOrString(containerPort)).endTcpSocket()
                                .endLivenessProbe()
                                .withNewResources()
                                    .addToRequests("cpu", new Quantity("50m"))
                                    .addToRequests("memory", new Quantity("64Mi"))
                                    .addToLimits("cpu", new Quantity("500m"))
                                    .addToLimits("memory", new Quantity("256Mi"))
                                .endResources()
                            .endContainer()
                        .endSpec()
                    .endTemplate()
                .endSpec()
                .build();
    }

    public static io.fabric8.kubernetes.api.model.Service trackService(String namespace, String serviceName, String appName, String track, int containerPort, UUID applicationId) {
        return new ServiceBuilder()
                .withNewMetadata()
                    .withName(serviceName)
                    .withNamespace(namespace)
                    .addToLabels(MANAGED_LABEL, MANAGED_BY)
                    .addToLabels(APPLICATION_LABEL, applicationId.toString())
                    .addToLabels(TRACK_LABEL, track)
                .endMetadata()
                .withNewSpec()
                    .addToSelector("app", appLabel(appName))
                    .addToSelector(TRACK_LABEL, track)
                    .addNewPort()
                        .withName("http")
                        .withPort(80)
                        .withTargetPort(new IntOrString(containerPort))
                        .withProtocol("TCP")
                    .endPort()
                .endSpec()
                .build();
    }

    public static HTTPRoute canaryRoute(String namespace, String name, String gatewayName, String stableService, String canaryService, int canaryWeight) {
        int weight = ReleasePolicies.canaryWeight(canaryWeight);
        return new HTTPRouteBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .addToLabels(MANAGED_LABEL, MANAGED_BY)
                .endMetadata()
                .withNewSpec()
                    .addNewParentRef()
                        .withName(gatewayName)
                        .withNamespace(namespace)
                    .endParentRef()
                    .addNewRule()
                        .addNewBackendRef()
                            .withName(stableService)
                            .withPort(80)
                            .withWeight(100 - weight)
                        .endBackendRef()
                        .addNewBackendRef()
                            .withName(canaryService)
                            .withPort(80)
                            .withWeight(weight)
                        .endBackendRef()
                    .endRule()
                .endSpec()
                .build();
    }

    public static HorizontalPodAutoscaler autoscaler(String namespace, String name, String deploymentName, int minReplicas, int maxReplicas, int cpuUtilization, int memoryUtilization, UUID applicationId) {
        return new HorizontalPodAutoscalerBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                    .addToLabels(MANAGED_LABEL, MANAGED_BY)
                    .addToLabels(APPLICATION_LABEL, applicationId.toString())
                .endMetadata()
                .withNewSpec()
                    .withMinReplicas(minReplicas)
                    .withMaxReplicas(maxReplicas)
                    .withNewScaleTargetRef()
                        .withApiVersion("apps/v1")
                        .withKind("Deployment")
                        .withName(deploymentName)
                    .endScaleTargetRef()
                    .addNewMetric()
                        .withType("Resource")
                        .withNewResource()
                            .withName("cpu")
                            .withNewTarget()
                                .withType("Utilization")
                                .withAverageUtilization(cpuUtilization)
                            .endTarget()
                        .endResource()
                    .endMetric()
                    .addNewMetric()
                        .withType("Resource")
                        .withNewResource()
                            .withName("memory")
                            .withNewTarget()
                                .withType("Utilization")
                                .withAverageUtilization(memoryUtilization)
                            .endTarget()
                        .endResource()
                    .endMetric()
                .endSpec()
                .build();
    }

    private static String appLabel(String deploymentName) {
        int suffix = deploymentName.lastIndexOf('-');
        if (suffix > 0) {
            String tail = deploymentName.substring(suffix + 1);
            if ("blue".equals(tail) || "green".equals(tail) || "stable".equals(tail) || "canary".equals(tail)) {
                return deploymentName.substring(0, suffix);
            }
        }
        return deploymentName;
    }
}
