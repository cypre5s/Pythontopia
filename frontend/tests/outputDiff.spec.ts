import { expect, it } from 'vitest';
import { outputDiff, visibleDiffText } from '../src/shared/outputDiff';

it('highlights missing Chinese punctuation without marking equal text', () => {
  const diff = outputDiff('你好，小林\n', '你好，小林！');
  expect(diff.actual).toEqual([{text:'你好，小林',changed:false}]);
  expect(diff.expected).toEqual([{text:'你好，小林',changed:false},{text:'！',changed:true}]);
});
it('marks extra text, replacements and internal whitespace, preserving Unicode', () => {
  expect(outputDiff('a  b', 'a b').actual.filter(x=>x.changed).map(visibleDiffText)).toEqual(['·']);
  expect(outputDiff('你好🙂多余', '你好🙂').actual.at(-1)).toEqual({text:'多余',changed:true});
  expect(outputDiff('-234','1')).toEqual({actual:[{text:'-234',changed:true}],expected:[{text:'1',changed:true}]});
  expect(visibleDiffText({text:'\t\n',changed:true})).toBe('⇥↵\n');
});
it('handles empty, matching, long and HTML-like outputs as plain bounded text', () => {
  expect(outputDiff('', 'x').actual).toEqual([]);
  expect(outputDiff('x\r\n', 'x').actual).toEqual([{text:'x',changed:false}]);
  const diff = outputDiff('a'.repeat(16000), '<script>'.repeat(2000));
  expect(diff.actual).toHaveLength(1);
  expect(diff.expected[0]?.text).toBe('<script>'.repeat(2000));
});
