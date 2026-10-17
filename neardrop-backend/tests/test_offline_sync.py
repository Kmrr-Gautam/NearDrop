def test_offline_sync_idempotency(client):
    # Setup User
    client.post("/api/v1/auth/register", json={
        "email": "offlineuser@example.com",
        "username": "offlineuser",
        "password": "offlinepassword123"
    })
    token = client.post("/api/v1/auth/login", json={
        "username_or_email": "offlineuser",
        "password": "offlinepassword123"
    }).json()["data"]["access_token"]
    headers = {"Authorization": f"Bearer {token}"}

    sync_payload = {
        "sessions": [
            {
                "idempotency_key": "offline-session-key-001",
                "sender_device_id": "phone-sender-offline",
                "receiver_device_id": "phone-receiver-offline",
                "status": "COMPLETED",
                "file_count": 2,
                "total_size_bytes": 204800,
                "files": [
                    {
                        "file_name": "photo_offline.jpg",
                        "mime_type": "image/jpeg",
                        "size_bytes": 102400,
                        "sha256_checksum": "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"
                    },
                    {
                        "file_name": "video_clip.mp4",
                        "mime_type": "video/mp4",
                        "size_bytes": 102400
                    }
                ],
                "events": [
                    {
                        "event_type": "LOCAL_TRANSFER_FINISH",
                        "message": "Direct peer-to-peer Wi-Fi transfer finished"
                    }
                ]
            }
        ]
    }

    # First sync
    res1 = client.post("/api/v1/transfers/sync", json=sync_payload, headers=headers)
    assert res1.status_code == 200
    data1 = res1.json()["data"]
    assert data1["synced_count"] == 1
    session_id_first = data1["session_ids"][0]

    # Re-syncing the same offline payload returns the same session ID without duplication
    res2 = client.post("/api/v1/transfers/sync", json=sync_payload, headers=headers)
    assert res2.status_code == 200
    data2 = res2.json()["data"]
    assert data2["session_ids"][0] == session_id_first
