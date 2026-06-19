package com.mesha.connector.agent;

import java.util.List;

record RegisterAgentRequest(String hostname, String executorType, String connectorVersion, List<String> capabilities) {}
