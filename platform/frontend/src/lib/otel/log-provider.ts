import { LoggerProvider, BatchLogRecordProcessor } from "@opentelemetry/sdk-logs";
import { OTLPLogExporter } from "@opentelemetry/exporter-logs-otlp-http";
import { Resource } from "@opentelemetry/resources";
import {
  ATTR_SERVICE_NAME,
  ATTR_SERVICE_VERSION,
} from "@opentelemetry/semantic-conventions";
import { logs, SeverityNumber } from "@opentelemetry/api-logs";
import type { Logger as OtelLogger } from "@opentelemetry/api-logs";
import { otelConfig } from "./config";

export type { OtelLogger };
export { SeverityNumber };

let _provider: LoggerProvider | null = null;

export function initLogProvider(): LoggerProvider | null {
  if (!otelConfig.enabled) return null;
  if (_provider) return _provider;

  const resource = new Resource({
    [ATTR_SERVICE_NAME]: otelConfig.serviceName,
    [ATTR_SERVICE_VERSION]: otelConfig.serviceVersion,
    "deployment.environment": otelConfig.environment,
  });

  const exporter = new OTLPLogExporter({
    url: `${otelConfig.otlpEndpoint}/v1/logs`,
    headers: otelConfig.authorizationHeader
      ? { Authorization: otelConfig.authorizationHeader }
      : undefined,
  });

  _provider = new LoggerProvider({ resource });
  _provider.addLogRecordProcessor(new BatchLogRecordProcessor(exporter));

  logs.setGlobalLoggerProvider(_provider);

  return _provider;
}

export function getOtelLogger(name = otelConfig.serviceName): OtelLogger {
  return logs.getLogger(name, otelConfig.serviceVersion);
}
