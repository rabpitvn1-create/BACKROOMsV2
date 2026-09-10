#!/usr/bin/env python3
import sys
import unicodedata
import xml.etree.ElementTree as ET


def norm(value):
    value = (value or '').replace('đ', 'd').replace('Đ', 'D').replace('’', "'")
    value = unicodedata.normalize('NFKD', value)
    return ''.join(ch for ch in value if not unicodedata.combining(ch)).casefold().strip()


def first_input(root):
    for node in root.iter('node'):
        if (node.attrib.get('class') or '').endswith('EditText'):
            return node
    return None


def first_submit(root):
    for node in root.iter('node'):
        cls = node.attrib.get('class') or ''
        rid = node.attrib.get('resource-id') or ''
        text = norm(node.attrib.get('text'))
        desc = norm(node.attrib.get('content-desc'))
        if rid.endswith(':id/submit') or (cls.endswith('Button') and (text == 'thuc hien' or desc == 'thuc hien')):
            return node
    return None


def main(path):
    root = ET.parse(path).getroot()
    input_node = first_input(root)
    submit_node = first_submit(root)
    if input_node is None:
        raise SystemExit('missing EditText input')
    if submit_node is None:
        raise SystemExit('missing Thuc hien button')
    print('input=' + (input_node.attrib.get('text') or ''))
    print('submit_bounds=' + (submit_node.attrib.get('bounds') or ''))


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('usage: emu-level1-selector-regression.py UI_XML')
    main(sys.argv[1])
