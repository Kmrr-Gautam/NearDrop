def test_unauthenticated_access_denied(client):
    assert client.get("/api/v1/auth/me").status_code == 401
    assert client.get("/api/v1/devices").status_code == 401
    assert client.get("/api/v1/transfers").status_code == 401


def test_cross_user_isolation(client):
    # User 1
    client.post("/api/v1/auth/register", json={
        "email": "user1@example.com", "username": "user1", "password": "pass123456"
    })
    token1 = client.post("/api/v1/auth/login", json={
        "username_or_email": "user1", "password": "pass123456"
    }).json()["data"]["access_token"]

    # User 2
    client.post("/api/v1/auth/register", json={
        "email": "user2@example.com", "username": "user2", "password": "pass123456"
    })
    token2 = client.post("/api/v1/auth/login", json={
        "username_or_email": "user2", "password": "pass123456"
    }).json()["data"]["access_token"]

    # User 1 creates device
    dev1_id = client.post("/api/v1/devices/register", json={
        "device_name": "User 1 Phone"
    }, headers={"Authorization": f"Bearer {token1}"}).json()["data"]["id"]

    # User 2 tries to access User 1's device -> 403 Forbidden
    resp = client.get(f"/api/v1/devices/{dev1_id}", headers={"Authorization": f"Bearer {token2}"})
    assert resp.status_code == 403


def test_zero_file_bytes_endpoint_guarantee(client):
    """Verifies that no endpoints exist for uploading or downloading actual binary file content."""
    # Attempt POST to an upload endpoint -> 404 / 405
    assert client.post("/api/v1/upload").status_code == 404
    assert client.post("/api/v1/transfers/upload").status_code == 404
    assert client.get("/api/v1/download").status_code == 404
    assert client.get("/api/v1/transfers/download").status_code == 404
