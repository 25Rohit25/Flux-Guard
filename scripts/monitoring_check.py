"""Verify that the live Compose monitoring stack has useful data."""

import base64
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

PROMETHEUS = os.getenv("PROMETHEUS_URL", "http://localhost:9091")
GRAFANA = os.getenv("GRAFANA_URL", "http://localhost:3000")
JOBS = {"gateway", "user-service", "product-service", "order-service"}


def config_value(name):
    if name in os.environ:
        return os.environ[name]
    env_file = Path(__file__).resolve().parents[1] / ".env"
    for line in env_file.read_text(encoding="utf-8").splitlines():
        if line.startswith(name + "="):
            return line.split("=", 1)[1]
    raise ValueError(f"Set {name} in the environment or .env")


def get_json(url, auth=None):
    headers = {}
    if auth:
        credentials = base64.b64encode(f"admin:{auth}".encode()).decode()
        headers["Authorization"] = "Basic " + credentials
    request = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(request, timeout=5) as response:
        return json.load(response)


def query(expression):
    url = PROMETHEUS + "/api/v1/query?" + urllib.parse.urlencode({"query": expression})
    result = get_json(url)
    assert result["status"] == "success", f"Prometheus rejected {expression}"
    return result["data"]["result"]


def check():
    password = config_value("GRAFANA_PASSWORD")
    datasource = get_json(GRAFANA + "/api/datasources/uid/fluxguard-prometheus", password)
    assert datasource["uid"] == "fluxguard-prometheus"
    assert datasource["url"] == "http://prometheus:9090"
    dashboard = get_json(GRAFANA + "/api/dashboards/uid/fluxguard-overview", password)
    panels = dashboard["dashboard"]["panels"]
    assert len(panels) == 8, f"Expected 8 dashboard panels, got {len(panels)}"
    assert all(panel["datasource"]["uid"] == datasource["uid"] for panel in panels)

    for _ in range(18):
        targets = get_json(PROMETHEUS + "/api/v1/targets?state=active")
        active = {
            target["labels"]["job"]
            for target in targets["data"]["activeTargets"]
            if target["health"] == "up"
        }
        if JOBS <= active:
            break
        time.sleep(5)
    else:
        raise AssertionError(f"Prometheus targets unhealthy: {JOBS - active}")

    for expression in (
        "spring_cloud_gateway_requests_seconds_count",
        "spring_cloud_gateway_requests_seconds_bucket",
        "fluxguard_rate_limited_total",
        'jvm_memory_used_bytes{area="heap"}',
    ):
        assert query(expression), f"No Prometheus samples for {expression}"
    print("All four Prometheus targets, dashboard, datasource, and key metrics are available")


if __name__ == "__main__":
    check()
