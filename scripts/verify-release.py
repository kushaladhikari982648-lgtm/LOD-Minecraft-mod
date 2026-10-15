#!/usr/bin/env python3
"""Validate the remapped release JAR, without requiring Minecraft at runtime."""
import json
import struct
import sys
import zipfile
from pathlib import Path

version = sys.argv[1]
jar = Path('build/libs') / f'pocketlod-{version}.jar'
with zipfile.ZipFile(jar) as archive:
    names = set(archive.namelist())
    mod = json.loads(archive.read('fabric.mod.json'))
    assert mod['id'] == 'pocketlod' and mod['version'] == version, 'Incorrect mod metadata'
    assert mod['environment'] == 'client', 'Incorrect environment'
    for entry in mod['entrypoints']['client']:
        assert entry.replace('.', '/') + '.class' in names, 'Missing entrypoint'
    for config_name in mod['mixins']:
        config = json.loads(archive.read(config_name))
        assert config['refmap'] in names, 'Missing mixin refmap'
        refmap = json.loads(archive.read(config['refmap']))
        assert refmap.get('mappings'), 'Empty mixin refmap'
        for mixin in config['client']:
            assert (config['package'] + '.' + mixin).replace('.', '/') + '.class' in names, 'Missing mixin'
    for suffix in ['json', 'vsh', 'fsh']:
        assert f'assets/pocketlod/shaders/core/terrain.{suffix}' in names, 'Missing shader'
    classes = [name for name in names if name.startswith('dev/pocketlod/') and name.endswith('.class')]
    assert classes, 'Missing classes'
    for name in classes:
        data = archive.read(name)
        assert data[:4] == b'\xca\xfe\xba\xbe', f'Invalid class: {name}'
        assert struct.unpack('>H', data[6:8])[0] == 61, f'Not Java 17 bytecode: {name}'
print(f'PASS: {jar}, {len(classes)} Java 17 classes, metadata, refmap, mixins and shaders')
