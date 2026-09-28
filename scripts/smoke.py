"""End-to-end Compose smoke and failure test, using only the Python standard library."""

import json
import subprocess
import time
import urllib.error
import urllib.request

BASE = "http://localhost:8080"


def call(method, path, token=None, body=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        response = urllib.request.urlopen(request, timeout=5)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        raw = response.read()
        payload = json.loads(raw) if raw else None
        return response.status, response.headers, payload


def expect(actual, expected, description):
    assert actual == expected, f"{description}: expected {expected}, got {actual}"


def main():
    for attempt in range(90):
        try:
            status, _, result = call("POST", "/auth/login", body={
                "email": "admin@example.test", "password": "ci-admin-password-123"})
            if status == 200:
                break
        except (urllib.error.URLError, OSError):
            pass
        time.sleep(2)
    else:
        raise AssertionError("Gateway and admin login did not become ready")

    admin = result["accessToken"]
    status, _, _ = call("POST", "/auth/register", body={
        "name": "Smoke User", "email": "smoke@example.test", "password": "smoke-password-123"})
    expect(status, 201, "registration")
    status, _, result = call("POST", "/auth/login", body={
        "email": "smoke@example.test", "password": "smoke-password-123"})
    expect(status, 200, "user login")
    user = result["accessToken"]
    status, _, profile = call("GET", "/api/users/me", user)
    expect(status, 200, "own profile through gateway")
    expect(profile["email"], "smoke@example.test", "own profile email")

    status, headers, _ = call("GET", "/api/products")
    expect(status, 401, "anonymous access")
    assert headers.get("X-Request-ID"), "Missing request ID"
    product = {"name": "Keyboard", "description": "Mechanical", "price": 49.99, "stock": 5}
    status, _, _ = call("POST", "/api/products", user, product)
    expect(status, 403, "non-admin product write")
    status, _, created = call("POST", "/api/products", admin, product)
    expect(status, 201, "admin product write")
    product_id = created["id"]
    status, _, _ = call("GET", f"/api/products/{product_id}", user)
    expect(status, 200, "product read")
    status, _, order = call("POST", "/api/orders", user, {
        "items": [{"productId": product_id, "quantity": 2}]})
    expect(status, 201, "order creation")
    expect(float(order["totalAmount"]), 99.98, "server-computed order total")
    status, _, _ = call("GET", f"/api/orders/{order['id']}", user)
    expect(status, 200, "order ownership")

    limited = False
    for _ in range(80):
        status, _, _ = call("GET", "/api/products", user)
        if status == 429:
            limited = True
            break
        expect(status, 200, "product read before rate limit")
    assert limited, "Token bucket did not reject a burst"

    subprocess.run(["docker", "compose", "stop", "product-service"], check=True)
    status, _, result = call("GET", f"/api/products/{product_id}", admin)
    expect(status, 503, "product failure fallback")
    print("Compose smoke, authorization, rate limit, and failure checks passed")


if __name__ == "__main__":
    main()
