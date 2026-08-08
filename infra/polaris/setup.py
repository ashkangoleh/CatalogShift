import json
import os
import urllib.error
import urllib.parse
import urllib.request

BASE_URL = "http://polaris:8181"
REALM = "POLARIS"


def request(path, method="GET", payload=None, headers=None, form=False):
    data = None
    request_headers = {"Accept": "application/json", "Polaris-Realm": REALM}
    if headers:
        request_headers.update(headers)
    if payload is not None:
        if form:
            data = urllib.parse.urlencode(payload).encode()
            request_headers["Content-Type"] = "application/x-www-form-urlencoded"
        else:
            data = json.dumps(payload).encode()
            request_headers["Content-Type"] = "application/json"
    req = urllib.request.Request(BASE_URL + path, data=data, headers=request_headers, method=method)
    with urllib.request.urlopen(req, timeout=30) as response:
        body = response.read()
        return json.loads(body) if body else None


client_id = os.environ["POLARIS_CLIENT_ID"]
client_secret = os.environ["POLARIS_CLIENT_SECRET"]
token_response = request(
    "/api/catalog/v1/oauth/tokens",
    method="POST",
    payload={
        "grant_type": "client_credentials",
        "client_id": client_id,
        "client_secret": client_secret,
        "scope": "PRINCIPAL_ROLE:ALL",
    },
    form=True,
)
headers = {"Authorization": f"Bearer {token_response['access_token']}"}
catalog = {
    "catalog": {
        "name": "migration",
        "type": "INTERNAL",
        "readOnly": False,
        "properties": {"default-base-location": "s3://warehouse/migration"},
        "storageConfigInfo": {
            "storageType": "S3",
            "allowedLocations": ["s3://warehouse/migration"],
            "endpoint": "http://minio:9000",
            "endpointInternal": "http://minio:9000",
            "pathStyleAccess": True,
            "region": "us-west-2",
            "stsUnavailable": True,
        },
    }
}
try:
    request("/api/management/v1/catalogs/migration", headers=headers)
except urllib.error.HTTPError as error:
    if error.code != 404:
        raise
    request("/api/management/v1/catalogs", method="POST", payload=catalog, headers=headers)
grant = {"type": "catalog", "privilege": "CATALOG_MANAGE_CONTENT"}
request(
    "/api/management/v1/catalogs/migration/catalog-roles/catalog_admin/grants",
    method="PUT",
    payload=grant,
    headers=headers,
)
print("Polaris catalog is ready")
