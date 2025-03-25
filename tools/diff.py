import json
import re
from pathlib import Path

import genson
import requests

token = "fill this in"
urls = {
    "prod": f"http://aquadx.hydev.org/gs/{token}/chu3/2.27",
    "staging": f"http://staging.aquadx.net/gs/{token}/chu3/2.27"
}


def save_resp(i, idx, api, data):
    url = urls[idx]
    resp = requests.post(f"{url}/{api}", json=data)
    txt = f"""
Request: {api}
{data}

Response: {resp.status_code}
{resp.text}
"""
    Path(f"test-diff/{idx}").mkdir(exist_ok=True, parents=True)
    Path(f"test-diff/{idx}/{i}-{api}.json").write_text(txt)
    Path(f"test-diff/{idx}-struct").mkdir(exist_ok=True, parents=True)
    Path(f"test-diff/{idx}-struct/{i}-{api}.json").write_text(json.dumps(to_structure(resp.json()), indent=2))


# def to_structure(d: dict):
#     # Convert the dictionary into type structure
#     # e.g. {a: "1", b: 2, c: [1, 2, 3], d: {e: true, f: [1, "b", "c"]}} -> {a: "str", b: "int", c: ["int"], d: {e: "bool", f: ["int", "str"]}}
#     # Make sure that the returned dict is sorted by key
#     def get_type(value):
#         # Handle dictionary
#         if isinstance(value, dict):
#             return {key: get_type(val) for key, val in value.items()}
#         # Handle list
#         elif isinstance(value, list):
#             # Get unique types from the list
#             unique_types = {get_type(item) for item in value}
#             if len(unique_types) == 1:
#                 return [unique_types.pop()]  # Homogeneous list
#             return list(unique_types)  # Heterogeneous list
#         else:
#             return type(value).__name__
#
#     return get_type(d)


def to_structure(d: dict):
    b = genson.SchemaBuilder()
    b.add_object(d)
    return sort_dict_keys(b.to_schema())


def sort_dict_keys(data):
    if isinstance(data, dict):
        # Sort the dictionary keys and recursively sort its values
        return {key: sort_dict_keys(data[key]) for key in sorted(data)}
    elif isinstance(data, list):
        # If the value is a list, sort each item in the list recursively
        return [sort_dict_keys(item) for item in data]
    else:
        # Return the value as is if it's not a dict or list
        return data


if __name__ == '__main__':
    # Read chusan.log
    d = Path("test.log").read_text('utf-8').splitlines()
    d = [l.split("|", 1)[1] for l in d if '|' in l]
    d = [l.split(": ", 1)[1] for l in d if ': ' in l]
    d = [l for l in d if l.startswith("Chu3 <") and 'Upsert' not in l]
    d = [l.split("< ", 1)[1] for l in d]
    d = [l.split(" : ") for l in d]  # (api, data)
    d = [(api, json.loads(data)) for api, data in d]

    print(d)

    for i, (a, dt) in enumerate(d):
        save_resp(i, "prod", a, dt)
        save_resp(i, "staging", a, dt)


