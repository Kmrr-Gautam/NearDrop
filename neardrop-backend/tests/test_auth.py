def test_register_user(client):
    response = client.post("/api/v1/auth/register", json={
        "email": "alice@example.com",
        "username": "alice",
        "password": "securepassword123",
        "full_name": "Alice Tester"
    })
    assert response.status_code == 201
    data = response.json()
    assert data["success"] is True
    assert data["data"]["email"] == "alice@example.com"
    assert data["data"]["username"] == "alice"


def test_login_user(client):
    # Register first
    client.post("/api/v1/auth/register", json={
        "email": "bob@example.com",
        "username": "bob",
        "password": "bobpassword123"
    })

    # Login
    response = client.post("/api/v1/auth/login", json={
        "username_or_email": "bob",
        "password": "bobpassword123"
    })
    assert response.status_code == 200
    data = response.json()
    assert data["success"] is True
    assert "access_token" in data["data"]
    assert "refresh_token" in data["data"]


def test_refresh_token(client):
    client.post("/api/v1/auth/register", json={
        "email": "charlie@example.com",
        "username": "charlie",
        "password": "charliepass123"
    })
    login_resp = client.post("/api/v1/auth/login", json={
        "username_or_email": "charlie",
        "password": "charliepass123"
    })
    refresh_token = login_resp.json()["data"]["refresh_token"]

    response = client.post("/api/v1/auth/refresh", json={
        "refresh_token": refresh_token
    })
    assert response.status_code == 200
    assert "access_token" in response.json()["data"]
