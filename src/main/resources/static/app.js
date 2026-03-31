// ── Markdown renderer ────────────────────────────────────
function renderMd(text) {
  return DOMPurify.sanitize(marked.parse(text || ''));
}

// ── Pricing constants ────────────────────────────────────
const INPUT_PRICE_PER_M  = 5.0;   // USD per 1M input tokens
const OUTPUT_PRICE_PER_M = 25.0;  // USD per 1M output tokens
const KO_TOK_PER_CHAR    = 1.5;   // Korean Hangul: ~1.5 tokens/char
const EN_TOK_PER_CHAR    = 0.25;  // ASCII: ~4 chars/token
const PROMPT_OVERHEAD    = 420;   // fixed prompt template tokens
const OUTLINE_TOKENS     = 650;   // estimated outline output tokens

const SPLIT_MARKER = '===OPENING_START===';

let isGenerating = false;

// ── Price helpers ────────────────────────────────────────
function estimateTokens(text) {
  const ko = (text.match(/[\uAC00-\uD7A3]/g) || []).length;
  return Math.ceil(ko * KO_TOK_PER_CHAR + (text.length - ko) * EN_TOK_PER_CHAR);
}

function updateSliderLabel() {
  const v = document.getElementById('lengthSlider').value;
  document.getElementById('sliderLabel').textContent = `약 ${v}자`;
}

function updatePrice() {
  const userText = ['genre', 'setting', 'characters', 'events', 'conditions']
    .map(id => document.getElementById(id).value || '')
    .join('');
  const length = parseInt(document.getElementById('lengthSlider').value);

  const inputTokens  = estimateTokens(userText) + PROMPT_OVERHEAD;
  const outputTokens = OUTLINE_TOKENS + Math.ceil(length * KO_TOK_PER_CHAR);
  const totalUSD     = (inputTokens / 1e6) * INPUT_PRICE_PER_M
                     + (outputTokens / 1e6) * OUTPUT_PRICE_PER_M;

  document.getElementById('estInput').textContent  = `~${inputTokens.toLocaleString()} tok`;
  document.getElementById('estOutput').textContent = `~${outputTokens.toLocaleString()} tok`;
  document.getElementById('estCost').textContent   = totalUSD < 0.001
    ? '< $0.001'
    : `$${totalUSD.toFixed(3)}`;
}

// ── UI helpers ───────────────────────────────────────────
function switchTab(btn, tab) {
  document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
  document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
  btn.classList.add('active');
  document.getElementById('tab-' + tab).classList.add('active');
}

function showError(msg) {
  const el = document.getElementById('errorMsg');
  el.textContent = msg;
  el.classList.add('visible');
}

function hideError() {
  document.getElementById('errorMsg').classList.remove('visible');
}

function setStatus(state, text) {
  document.getElementById('statusDot').className = 'status-dot ' + state;
  document.getElementById('statusText').textContent = text;
}

function copyAll() {
  const outline = document.getElementById('outlineBox').textContent;
  const opening = document.getElementById('openingBox').textContent;
  navigator.clipboard.writeText(
    `=== 전체 개요 ===\n\n${outline}\n\n=== 소설 앞부분 ===\n\n${opening}`
  ).then(() => showToast('클립보드에 복사됨'));
}

function showToast(msg) {
  const t = document.createElement('div');
  t.textContent = msg;
  Object.assign(t.style, {
    position: 'fixed', bottom: '1.5rem', left: '50%', transform: 'translateX(-50%)',
    background: 'rgba(124,106,247,0.9)', color: '#fff', borderRadius: '8px',
    padding: '0.6rem 1.25rem', fontSize: '0.88rem', fontWeight: '500',
    boxShadow: '0 4px 20px rgba(0,0,0,0.3)', zIndex: '9999', transition: 'opacity 0.3s'
  });
  document.body.appendChild(t);
  setTimeout(() => { t.style.opacity = '0'; setTimeout(() => t.remove(), 300); }, 1800);
}

// ── Generate ─────────────────────────────────────────────
async function generate() {
  if (isGenerating) return;
  hideError();

  const genre      = document.getElementById('genre').value;
  const setting    = document.getElementById('setting').value.trim();
  const characters = document.getElementById('characters').value.trim();
  const events     = document.getElementById('events').value.trim();
  const conditions = document.getElementById('conditions').value.trim();
  const length     = parseInt(document.getElementById('lengthSlider').value);

  if (!genre)      { showError('장르를 선택해 주세요.'); return; }
  if (!characters) { showError('등장인물을 입력해 주세요.'); return; }
  if (!events)     { showError('핵심 사건을 입력해 주세요.'); return; }

  isGenerating = true;
  document.getElementById('generateBtn').disabled = true;
  document.getElementById('generateBtn').textContent = '⏳ 생성 중...';

  document.getElementById('outputSection').classList.add('visible');
  const outlineBox = document.getElementById('outlineBox');
  const openingBox = document.getElementById('openingBox');
  outlineBox.innerHTML = '';
  openingBox.innerHTML = '';
  outlineBox.classList.add('streaming');
  openingBox.classList.remove('streaming');
  switchTab(document.querySelector('[data-tab="outline"]'), 'outline');
  setStatus('generating', '개요 생성 중...');

  let fullText  = '';
  let splitDone = false;

  try {
    const resp = await fetch('/api/generate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ genre, setting, characters, events, conditions, length })
    });

    if (!resp.ok) {
      if (resp.status === 429) throw new Error('요청이 너무 많습니다. 1분 후 다시 시도해 주세요.');
      throw new Error(`서버 오류: HTTP ${resp.status}`);
    }

    const reader  = resp.body.getReader();
    const decoder = new TextDecoder();
    let lineBuf   = '';
    let curEvent  = '';
    let curData   = '';

    outer: while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      lineBuf += decoder.decode(value, { stream: true });
      const lines = lineBuf.split('\n');
      lineBuf = lines.pop();

      for (const raw of lines) {
        const trimmed = raw.trimEnd();

        // 빈 줄 = 이벤트 블록 끝 → dispatch
        if (trimmed === '') {
          if (curEvent === 'error') throw new Error(curData);
          if (curEvent === 'done')  break outer;

          if (curEvent === 'delta' && curData !== '') {
            fullText += curData;
            const idx = fullText.indexOf(SPLIT_MARKER);

            if (idx === -1) {
              outlineBox.innerHTML = renderMd(fullText);
            } else {
              if (!splitDone) {
                splitDone = true;
                outlineBox.classList.remove('streaming');
                outlineBox.innerHTML = renderMd(fullText.slice(0, idx).trim());
                openingBox.classList.add('streaming');
                setStatus('generating', '소설 앞부분 생성 중...');
              }
              openingBox.innerHTML = renderMd(fullText.slice(idx + SPLIT_MARKER.length).trim());
            }
          }

          curEvent = '';
          curData  = '';
          continue;
        }

        if (trimmed.startsWith('event:')) {
          curEvent = trimmed.slice(6).trim();
          continue;
        }
        // data: 줄은 raw 사용 — trimEnd()하면 줄 끝 공백이 사라져 단어가 붙음
        if (raw.startsWith('data:')) {
          const chunk = raw.slice(5);
          curData = curData ? curData + '\n' + chunk : chunk;
        }
      }
    }

    // Final render
    const finalIdx = fullText.indexOf(SPLIT_MARKER);
    if (finalIdx !== -1) {
      outlineBox.innerHTML = renderMd(fullText.slice(0, finalIdx).trim());
      openingBox.innerHTML = renderMd(fullText.slice(finalIdx + SPLIT_MARKER.length).trim());
    } else {
      outlineBox.innerHTML = renderMd(fullText.trim());
    }
    setStatus('done', '생성 완료');

  } catch (e) {
    setStatus('', '오류 발생');
    showError('오류: ' + e.message);
  } finally {
    outlineBox.classList.remove('streaming');
    openingBox.classList.remove('streaming');
    isGenerating = false;
    document.getElementById('generateBtn').disabled = false;
    document.getElementById('generateBtn').textContent = '🪄 소설 생성하기';
  }
}

window.onload = () => {
  updateSliderLabel();
  updatePrice();

  // 가격 업데이트
  ['genre', 'setting', 'characters', 'events', 'conditions'].forEach(id => {
    document.getElementById(id).addEventListener('input', updatePrice);
  });
  document.getElementById('lengthSlider').addEventListener('input', () => {
    updateSliderLabel();
    updatePrice();
  });

  // 버튼
  document.getElementById('generateBtn').addEventListener('click', generate);
  document.getElementById('copyBtn').addEventListener('click', copyAll);

  // 탭
  document.querySelectorAll('.tab-btn').forEach(btn => {
    btn.addEventListener('click', () => switchTab(btn, btn.dataset.tab));
  });
};
