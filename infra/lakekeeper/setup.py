import json
import os
import urllib.error
import urllib.request

BASE_URL = "http://lakekeeper:8181"


def get(path):
    req = urllib.request.Request(
        BASE_URL + path,
        headers={"Accept": "application/json"},
        method="GET",
    )
    with urllib.request.urlopen(req, timeout=30) as response:
        return json.loads(response.read())


def post(path, payload, accepted=(200, 201, 204, 409)):
    data = json.dumps(payload).encode()
    req = urllib.request.Request(
        BASE_URL + path,
        data=data,
        headers={"Accept": "application/json", "Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            if response.status not in accepted:
                raise RuntimeError(f"Unexpected HTTP status: {response.status}")
    except urllib.error.HTTPError as error:
        body = json.loads(error.read())
        error_type = body.get("error", {}).get("type")
        if error.code == 400 and error_type == "CatalogAlreadyBootstrapped":
            return
        if error.code not in accepted:
            raise


post("/management/v1/bootstrap", {"accept-terms-of-use": True})
warehouse = {
    "warehouse-name": "migration",
    "project-id": "00000000-0000-0000-0000-000000000000",
    "storage-profile": {
        "type": "s3",
        "bucket": "warehouse",
        "key-prefix": "migration",
        "endpoint": "http://minio:9000",
        "region": "us-west-2",
        "path-style-access": True,
        "flavor": "s3-compat",
        "sts-enabled": False,
        "remote-signing-enabled": True,
    },
    "storage-credential": {
        "type": "s3",
        "credential-type": "access-key",
        "access-key-id": os.environ["MINIO_ROOT_USER"],
        "secret-access-key": os.environ["MINIO_ROOT_PASSWORD"],
    },
}
warehouses = get("/management/v1/warehouse")
if not any(item["name"] == "migration" for item in warehouses["warehouses"]):
    post("/management/v1/warehouse", warehouse)
print("Lakekeeper warehouse is ready")
