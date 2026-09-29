"""Extract reusable vector artwork from the supplied supporter badge SVGs.

Run from the project root. SVG sources are kept beside PNG assets used by the
Java2D renderer; rasterize the SVGs at their intrinsic sizes after editing them.
"""
from copy import deepcopy
from pathlib import Path
import xml.etree.ElementTree as ET

NS = 'http://www.w3.org/2000/svg'
ET.register_namespace('', NS)
OUTPUT = Path('src/main/resources/META-INF/resources/static/images/2027')


def svg(width, height, viewbox=None):
    return ET.Element(f'{{{NS}}}svg', width=str(width), height=str(height),
                      viewBox=viewbox or f'0 0 {width} {height}')


def save(root, name):
    ET.ElementTree(root).write(OUTPUT / name, encoding='unicode')


for theme in ['dark', 'light']:
    source = ET.parse(f'docs/artwork/2027/jchampions-supporter-{theme}.svg').getroot()[-1]
    ring = [g for g in source if g.get('id', '').startswith('Ring-segment') and g.get('opacity') == '1']
    monogram = [g for g in source if g.get('id', '').startswith('Monogram-')]
    wordmark = [g for g in source if g.get('id', '').startswith('Wordmark-') or g.get('id') == 'Conference-descriptor']
    logo = svg(480, 112)
    mark = ET.SubElement(logo, f'{{{NS}}}g', transform='translate(0 8) scale(0.3) translate(-62 -120)')
    mark.extend(deepcopy(ring + monogram))
    word = ET.SubElement(logo, f'{{{NS}}}g', transform='translate(120 10) scale(0.62) translate(-503 -100)')
    word.extend(deepcopy(wordmark))
    save(logo, f'logo-{theme}.svg')
    frame = svg(400, 400, '62 120 320 320')
    frame.extend(deepcopy(ring))
    save(frame, f'portrait-ring-{theme}.svg')
    for width, height, shape in [(1280, 720, 'wide'), (1080, 1080, 'square')]:
        root = svg(width, height)
        defs = ET.SubElement(root, f'{{{NS}}}defs')
        gradient = ET.SubElement(defs, f'{{{NS}}}linearGradient', id='background', x1='0', y1='0', x2='1', y2='1')
        colors = ['#091D2A', '#194456'] if theme == 'dark' else ['#FFFFFF', '#E6F0F5']
        for offset, color in zip(['0%', '100%'], colors):
            ET.SubElement(gradient, f'{{{NS}}}stop', offset=offset, attrib={'stop-color': color})
        ET.SubElement(root, f'{{{NS}}}rect', width=str(width), height=str(height), fill='url(#background)')
        ambient = ET.SubElement(root, f'{{{NS}}}g', opacity='0.10', transform=f'translate({width - 50} {height // 2}) scale(2.8) translate(-222 -280)')
        ambient.extend(deepcopy(ring))
        ET.SubElement(root, f'{{{NS}}}rect', width=str(width), height='3', fill='#FF691B')
        save(root, f'background-{theme}-{shape}.svg')
