'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const source = fs.readFileSync(
  path.join(__dirname, '../app/src/main/assets/inventory-ui.js'),
  'utf8'
);

test('character Set Equip is hidden from Inventory presentation', () => {
  [
    'huyết ma kiếm',
    'huyết ma chiến khải',
    'vạn tàng giới',
    'tịch quang kiếm',
    'thiên cơ bạch kim kiếm khải',
    'sru recon frame r03',
    'ivory & ebony',
    'godkiller',
    'lucifer armor'
  ].forEach((name) => assert.ok(source.includes("'" + name + "':true"), name));
  assert.match(source, /function isCharacterSetEquipment\(item\)/);
  assert.match(source, /item\.characterSet===true\|\|item\.setEquipment===true/);
  assert.match(source, /function visibleInventoryItems\(id\)/);
  assert.match(source, /ownerInventory\(id\)\.filter\(function\(item\)\{return item&&!isCharacterSetEquipment\(item\);\}\)/);
  assert.match(source, /var items=visibleInventoryItems\(selectedOwnerId\)/);
});

test('Set Equip filtering changes presentation only, not stored inventory state', () => {
  assert.doesNotMatch(source, /state\.inventory\s*=\s*visibleInventoryItems/);
  assert.doesNotMatch(source, /member\.inventory\s*=\s*visibleInventoryItems/);
  assert.doesNotMatch(source, /splice\(|\.remove\(/);
});
