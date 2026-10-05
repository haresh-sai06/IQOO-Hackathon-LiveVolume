/**
 * Verification for the parser inside tools/docx-to-google-form.gs.
 *
 * Runs the real Apps Script code in Node, with only the Drive/zip calls stubbed
 * (they only exist inside Apps Script). Two suites:
 *
 *   1. synthetic  — hand-built paragraph lists covering the heuristics
 *   2. real file  — genuine word/document.xml from a python-docx .docx
 *
 *   python tools/make-survey-fixture.py     # once, for suite 2
 *   node   tools/docx-parser-check.js
 *
 * This is not part of the Android app and nothing here is wired into Gradle.
 */
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const ROOT = path.join(__dirname, '..');
const SCRIPT = path.join(ROOT, 'tools', 'docx-to-google-form.gs');
const FIXTURE = path.join(ROOT, 'build', 'fixtures', 'document.xml');

/* ---------------- harness ---------------- */

function loadScript(documentXml) {
  const ctx = {
    MimeType: { GOOGLE_DOCS: 'application/vnd.google-apps.document' },
    Logger: { log: () => {} },
    FormApp: {},
    Utilities: {
      unzip: () => [
        { getName: () => 'docProps/core.xml' },
        {
          getName: () => 'word/document.xml',
          getDataAsString: () => documentXml,
        },
      ],
    },
    DriveApp: {
      getFilesByName: () => ({
        hasNext: () => true,
        next: () => ({
          getMimeType: () =>
            'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
          getBlob: () => ({ getName: () => 'survey.docx', getBytes: () => [0] }),
        }),
      }),
    },
    console,
  };
  vm.createContext(ctx);
  vm.runInContext(fs.readFileSync(SCRIPT, 'utf8'), ctx);
  return ctx;
}

let failures = 0;
let checks = 0;

function expect(cond, msg) {
  checks++;
  if (!cond) {
    failures++;
    console.log('      FAIL: ' + msg);
  }
}

const flat = (plan) =>
  plan.sections.reduce((acc, s) => acc.concat(s.questions), []);

function kindOf(q) {
  if (q.kind === 'SCALE' && q.scale) return 'SCALE ' + q.scale.low + '-' + q.scale.high;
  if (q.options.length) {
    if (q.optionType === 'CHECKBOX') return 'CHECKBOXES';
    return q.options.length > 8 ? 'DROPDOWN' : 'MULTIPLE_CHOICE';
  }
  return q.kind;
}

function printPlan(heading, plan) {
  console.log('\n### ' + heading);
  console.log('    title: ' + plan.title);
  if (plan.description) console.log('    description: ' + plan.description);
  plan.sections.forEach((sec) => {
    if (sec.title) console.log('    [SECTION] ' + sec.title);
    sec.questions.forEach((q) => {
      console.log(
        '      (' + kindOf(q) + (q.required ? '/required' : '') + ') ' + q.title
      );
      q.options.forEach((o) => console.log('            - ' + o));
    });
  });
  plan.notes.forEach((n) => console.log('    ! ' + n));
}

/* ---------------- suite 1: heuristics ---------------- */

const H = (t) => ({ text: t, style: 'Heading1', isList: false, bold: false });
const H2 = (t) => ({ text: t, style: 'Heading2', isList: false, bold: false });
const P = (t, isList = false) => ({ text: t, style: '', isList, bold: false });

function suiteHeuristics(plan) {
  console.log('\n========== SUITE 1: HEURISTICS ==========');

  const r1 = plan([
    H('Customer Feedback Survey'),
    P('Thanks for your time.'),
    P('1. How satisfied are you with our service?'),
    P('Very satisfied', true),
    P('Somewhat satisfied', true),
    P('Neutral', true),
    P('Dissatisfied', true),
    P('2. Which features do you use?'),
    P('Live volume', true),
    P('Contacts', true),
    P('3. Any comments?'),
  ]);
  printPlan('numbered questions + bullets', r1);
  expect(r1.title === 'Customer Feedback Survey', 'title from Heading1');
  expect(flat(r1)[0].options.length === 4, 'Q1 four options');
  expect(flat(r1)[1].options.length === 2, 'Q2 two options');
  expect(flat(r1)[2].kind === 'PARAGRAPH', 'comments -> paragraph');

  // The trap: numbered options beneath a numbered question.
  const r2 = plan([
    P('4. Do you own a car?'),
    P('1. Yes'),
    P('2. No'),
    P('5. How old are you?'),
  ]);
  printPlan('numbered options under a question', r2);
  expect(flat(r2).length === 2, 'two questions, got ' + flat(r2).length);
  expect(
    flat(r2)[0].options.join('|') === 'Yes|No',
    'numbered lines read as options, got ' + flat(r2)[0].options.join('|')
  );
  expect(flat(r2)[1].title === 'How old are you?', 'sequential number starts a question');
  expect(flat(r2)[1].kind === 'SHORT_ANSWER', 'short "how" question stays short answer');

  const r3 = plan([
    P('6. Which integrations do you need? *'),
    P('[ ] Slack'),
    P('[ ] GitHub'),
    P('[x] Jira'),
    P('7. Describe your ideal workflow. (required)'),
  ]);
  printPlan('checkboxes + required', r3);
  expect(flat(r3)[0].optionType === 'CHECKBOX', 'checkbox glyphs detected');
  expect(flat(r3)[0].required === true, 'trailing asterisk means required');
  expect(flat(r3)[0].options.length === 3, 'three integrations');
  expect(flat(r3)[1].required === true, 'parenthetical (required) honoured');
  expect(
    flat(r3)[1].title === 'Describe your ideal workflow.',
    'parenthetical stripped from title'
  );

  const r4 = plan([
    H('Section A'),
    P('8. Rate our support on a scale of 1-5'),
    P('1 - Poor', true),
    P('5 - Excellent', true),
    H2('Section B'),
    P('9. Date of birth'),
    P('10. Email'),
  ]);
  printPlan('scale, sections, date', r4);
  expect(flat(r4)[0].kind === 'SCALE', 'scale kept as a scale, got ' + flat(r4)[0].kind);
  expect(flat(r4)[1].kind === 'DATE', 'date of birth -> date');
  expect(r4.sections.length === 2, 'two sections, got ' + r4.sections.length);
  expect(r4.notes.length === 0, 'no spurious notes: ' + r4.notes.join(' / '));

  // A scale with five labelled points is better as a multiple-choice question.
  const r4b = plan([
    P('11. Rate us on a scale of 1-5'),
    P('1 Terrible', true),
    P('2 Poor', true),
    P('3 OK', true),
    P('4 Good', true),
    P('5 Great', true),
  ]);
  printPlan('over-labelled scale', r4b);
  expect(flat(r4b)[0].kind === 'CHOICE', 'demoted to a choice, got ' + flat(r4b)[0].kind);
  expect(flat(r4b)[0].options.length === 5, 'all five labels kept');

  const r5 = plan([
    P('12. Which country?'),
    P('USA', true), P('UK', true), P('India', true), P('Germany', true),
    P('France', true), P('Japan', true), P('Brazil', true), P('Kenya', true),
    P('Canada', true), P('Spain', true),
    P('13. Did you enjoy it?'),
    P('a) Yes'),
    P('b) No'),
  ]);
  printPlan('dropdown + letter markers', r5);
  expect(flat(r5)[0].options.length === 10, 'ten countries');
  expect(flat(r5)[0].options.length > 8, 'long list becomes a dropdown');
  expect(flat(r5)[1].options.join('|') === 'Yes|No', 'letter markers are options');

  const r6 = plan([
    H('Onboarding'),
    P('What is your name?'),
    P('How did you hear about us?'),
    P('Anything else we should know?'),
  ]);
  printPlan('unnumbered questions', r6);
  expect(flat(r6).length === 3, 'three unnumbered questions found');
}

/* ---------------- suite 2: a real .docx ---------------- */

function suiteRealFile(ctx) {
  console.log('\n========== SUITE 2: REAL .DOCX ==========');
  if (!fs.existsSync(FIXTURE)) {
    console.log('    skipped — no fixture. Run: python tools/make-survey-fixture.py');
    return;
  }

  const blocks = ctx.readDocxBlocks_();
  const plan = ctx.planForm_(blocks);

  console.log('    --- paragraphs (LIST = detected as a list item) ---');
  blocks.forEach((b) =>
    console.log(
      '    ' +
        (b.isList ? 'LIST' : '    ') +
        ' ' +
        (b.style || '-').padEnd(12) +
        ' ' +
        b.text
    )
  );

  printPlan('parsed from a real python-docx file', plan);

  const f = flat(plan);
  const listCount = blocks.filter((b) => b.isList).length;

  // Word applies list numbering through the paragraph STYLE, not inline
  // <w:numPr>. Checking only for numPr silently loses every option.
  expect(listCount === 9, 'nine list paragraphs detected, got ' + listCount);
  expect(plan.title === 'Customer Feedback Survey', 'title from Heading1');
  expect(
    plan.description.indexOf('Thanks for taking the time') === 0,
    'intro paragraph became the form description'
  );
  expect(f.length === 9, 'nine questions, got ' + f.length);
  expect(f[0].options.length === 4, 'Q1 four options from ListBullet, got ' + f[0].options.length);
  expect(
    f[1].options.join('|') === 'Live volume|Contacts|Call history',
    'Q2 options, got ' + f[1].options.join('|')
  );
  expect(f[2].kind === 'PARAGRAPH', 'Q3 comments -> paragraph');
  expect(f[3].optionType === 'CHECKBOX', 'Q4 checkboxes');
  expect(f[3].required === true, 'Q4 required');
  expect(f[3].options.length === 3, 'Q4 three options');
  expect(f[4].kind === 'SCALE', 'Q5 scale, got ' + f[4].kind);
  expect(f[5].kind === 'DATE', 'Q6 date');
  expect(
    f[6].options.join('|') === 'Yes|No',
    'Q7 numbered options, got ' + f[6].options.join('|')
  );
  expect(f[8].kind === 'PARAGRAPH', 'Q9 anything-else -> paragraph');
  expect(plan.sections.length === 4, 'three sections + preamble, got ' + plan.sections.length);
  expect(plan.notes.length === 0, 'no human notes: ' + plan.notes.join(' / '));
}

/* ---------------- run ---------------- */

const ctx = loadScript(fs.existsSync(FIXTURE) ? fs.readFileSync(FIXTURE, 'utf8') : '');
suiteHeuristics(ctx.planForm_);
suiteRealFile(ctx);

console.log('\n=======================================');
console.log(
  failures === 0
    ? 'ALL ' + checks + ' CHECKS PASSED'
    : failures + ' of ' + checks + ' CHECKS FAILED'
);
process.exit(failures === 0 ? 0 : 1);
