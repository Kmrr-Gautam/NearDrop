def test_device_lifecycle(client):
    # Register & Login
    client.post("/api/v1/auth/register", json={
        "email": "devuser@example.com",
        "username": "devuser",
        "password": "devpassword123"
    })
    login_resp = client.post("/api/v1/auth/login", json={
        "username_or_email": "devuser",
        "password": "devpassword123"
    })
    token = login_resp.json()["data"]["access_token"]
    headers = {"Authorization": f"Bearer {token}"}

    # Register Device
    reg_resp = client.post("/api/v1/devices/register", json={
        "device_name": "Pixel 8 Pro",
        "device_model": "Google Pixel 8",
        "android_version": "14",
        "app_version": "0.2.2"
    }, headers=headers)
    assert reg_resp.status_code == 201
    device_id = reg_resp.json()["data"]["id"]

    # List Devices
    list_resp = client.get("/api/v1/devices", headers=headers)
    assert list_resp.status_code == 200
    assert len(list_resp.json()["data"]) >= 1

    # Get Device
    get_resp = client.get(f"/api/v1/devices/{device_id}", headers=headers)
    assert get_resp.status_code == 200
    assert get_resp.json()["data"]["device_name"] == "Pixel 8 Pro"

    # Delete Device
    del_resp = client.delete(f"/api/v1/devices/{device_id}", headers=headers)
    assert del_resp.status_code == 200
