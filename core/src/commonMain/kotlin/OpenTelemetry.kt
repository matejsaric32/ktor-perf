package hr.algebra.perf

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk
import io.opentelemetry.semconv.ServiceAttributes

fun getOpenTelemetry(serviceName : String) : OpenTelemetry {
    // Disable metrics exporter because the `jaegertracing/all-in-one`image, which we use in the example,
    // does not support OpenTelemetry metrics, so we prevent unnecessary configuration or warnings.
    System.setProperty("otel.metrics.exporter", "none")

    // The OTLP traces exporter defaults to localhost:4317. When no collector endpoint is configured
    // (e.g. in a Kubernetes deployment without an OTel collector sidecar), the SDK logs repeated
    // "Failed to export spans" errors. Default the traces exporter to "none" unless a collector is
    // explicitly configured via the standard OTEL_EXPORTER_OTLP_ENDPOINT / OTEL_TRACES_EXPORTER env vars.
    val hasOtlpEndpoint = !System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT").isNullOrBlank() ||
        !System.getenv("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT").isNullOrBlank()
    val tracesExporterConfigured = !System.getenv("OTEL_TRACES_EXPORTER").isNullOrBlank() ||
        System.getProperty("otel.traces.exporter") != null
    if (!hasOtlpEndpoint && !tracesExporterConfigured) {
        System.setProperty("otel.traces.exporter", "none")
    }

    return AutoConfiguredOpenTelemetrySdk.builder().addResourceCustomizer { oldResource, _ ->
        oldResource.toBuilder()
            .putAll(oldResource.attributes)
            .put(ServiceAttributes.SERVICE_NAME, serviceName)
            .build()
    }.build().openTelemetrySdk
}

