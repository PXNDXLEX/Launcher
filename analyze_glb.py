import json, struct, sys

def analyze_glb(filepath):
    try:
        with open(filepath, 'rb') as f:
            magic = f.read(4)
            if magic != b'glTF': 
                print(f"--- {filepath} --- Not GLB")
                return
            version, length = struct.unpack('<II', f.read(8))
            chunk_len, chunk_type = struct.unpack('<II', f.read(8))
            if chunk_type != 0x4E4F534A: 
                print(f"--- {filepath} --- No JSON chunk")
                return
            json_data = f.read(chunk_len).decode('utf-8')
            data = json.loads(json_data)
            print(f'--- {filepath} ---')
            print(f"Extensions: {data.get('extensionsUsed', [])}")
            for acc in data.get('accessors', []):
                if acc.get('type') == 'VEC3' and 'max' in acc and 'min' in acc:
                    print(f"Position bounds: min={acc['min']}, max={acc['max']}")
                    break
    except Exception as e:
        print(f"Error reading {filepath}: {e}")

analyze_glb('app/src/main/assets/models/Sedan.glb')
analyze_glb('app/src/main/assets/models/Stylus.glb')
analyze_glb('app/src/main/assets/models/Corsa.glb')
