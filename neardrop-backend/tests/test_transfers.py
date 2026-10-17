def test_transfer_session_full_lifecycle(client):
    # Setup Sender
    client.post("/api/v1/auth/register", json={
        "email": "sender@example.com",
        "username": "sender",
        "password": "senderpassword123"
    })
    sender_login = client.post("/api/v1/auth/login", json={
        "username_or_email": "sender",
        "password": "senderpassword123"
    }).json()["data"]["access_token"]
    sender_headers = {"Authorization": f"Bearer {sender_login}"}

    # Register Sender Device
    sender_dev_id = client.post("/api/v1/devices/register", json={
        "device_name": "Sender Galaxy",
        "device_model": "Samsung S24"
    }, headers=sender_headers).json()["data"]["id"]

    # 1. Create Session
    create_resp = client.post("/api/v1/transfers", json={
        "sender_device_id": sender_dev_id,
        "file_count": 1,
        "total_size_bytes": 1024,
        "files": [
            {
                "file_name": "test_document.pdf",
                "mime_type": "application/pdf",
                "size_bytes": 1024,
                "sha256_checksum": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
            }
        ]
    }, headers=sender_headers)
    assert create_resp.status_code == 201
    session_data = create_resp.json()["data"]
    session_id = session_data["id"]
    session_code = session_data["session_code"]
    assert len(session_code) == 8

    # 2. Join Session
    join_resp = client.post(f"/api/v1/transfers/{session_id}/join", json={
        "session_code": session_code,
        "receiver_device_id": "receiver-uuid-1234"
    }, headers=sender_headers)
    assert join_resp.status_code == 200
    assert join_resp.json()["data"]["status"] == "CONNECTED"

    # 3. Start Transfer
    start_resp = client.post(f"/api/v1/transfers/{session_id}/start", headers=sender_headers)
    assert start_resp.status_code == 200
    assert start_resp.json()["data"]["status"] == "IN_PROGRESS"

    # 4. Record Event
    event_resp = client.post(f"/api/v1/transfers/{session_id}/events", json={
        "event_type": "CHUNK_ACK",
        "message": "Chunk 1 ACK received"
    }, headers=sender_headers)
    assert event_resp.status_code == 201

    # 5. Complete Transfer
    comp_resp = client.post(f"/api/v1/transfers/{session_id}/complete", headers=sender_headers)
    assert comp_resp.status_code == 200
    assert comp_resp.json()["data"]["status"] == "COMPLETED"
