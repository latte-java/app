// Copy buttons inside <code-block> — clicking copies the data-copy value to
// the clipboard and flashes a "Copied" label.

class CopyButton {
  static SELECTOR = 'button[data-copy]';
  static REVERT_MS = 1200;

  static attach() {
    document.addEventListener('click', (e) => {
      const btn = e.target.closest(CopyButton.SELECTOR);
      if (!btn) return;
      CopyButton.copy(btn);
    });
  }

  static async copy(btn) {
    const text = btn.getAttribute('data-copy') || '';
    try {
      await navigator.clipboard?.writeText(text);
    } catch (e) {
    }
    const label = btn.querySelector('span:last-of-type');
    if (!label) return;
    const previous = label.textContent;
    label.textContent = 'Copied';
    btn.style.color = 'var(--color-sky-400)';
    setTimeout(() => {
      label.textContent = previous;
      btn.style.color = '';
    }, CopyButton.REVERT_MS);
  }
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', () => CopyButton.attach());
} else {
  CopyButton.attach();
}
