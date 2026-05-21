// Theme switcher — segmented control with system / light / dark. Loaded
// synchronously in <head> so the initial theme is applied to <html> before
// first paint (no light→dark flash). DOM-dependent work (button click handlers,
// initial aria-pressed sync) waits for DOMContentLoaded.

class ThemeSwitcher {
  static STORAGE_KEY = 'theme';
  static MODES = ['system', 'light', 'dark'];
  static SELECTOR = '[data-theme-set]';

  constructor() {
    this.mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    this.apply(this.read());
    this.mediaQuery.addEventListener('change', () => {
      if (this.read() === 'system') this.apply('system');
    });
  }

  read() {
    try {
      const stored = localStorage.getItem(ThemeSwitcher.STORAGE_KEY);
      if (ThemeSwitcher.MODES.includes(stored)) return stored;
    } catch (e) {
    }
    return 'system';
  }

  write(mode) {
    try {
      localStorage.setItem(ThemeSwitcher.STORAGE_KEY, mode);
    } catch (e) {
    }
  }

  apply(mode) {
    const dark = mode === 'dark' || (mode === 'system' && this.mediaQuery.matches);
    document.documentElement.classList.toggle('dark', dark);
  }

  set(mode) {
    if (!ThemeSwitcher.MODES.includes(mode)) return;
    this.write(mode);
    this.apply(mode);
    this.syncButtons(mode);
  }

  syncButtons(mode) {
    document.querySelectorAll(ThemeSwitcher.SELECTOR).forEach((btn) => {
      btn.setAttribute('aria-pressed', String(btn.getAttribute('data-theme-set') === mode));
    });
  }

  attach() {
    document.addEventListener('click', (e) => {
      const btn = e.target.closest(ThemeSwitcher.SELECTOR);
      if (!btn) return;
      this.set(btn.getAttribute('data-theme-set'));
    });
    this.syncButtons(this.read());
  }
}

// Instantiate immediately so apply() runs before first paint.
const themeSwitcher = new ThemeSwitcher();

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', () => themeSwitcher.attach());
} else {
  themeSwitcher.attach();
}
