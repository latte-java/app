// Latte repository portal — minimal client JS.
// SSR is the default; this only adds copy-to-clipboard and form auto-submits.

(function () {
    // Copy buttons inside <code-block>
    document.addEventListener('click', function (e) {
        const btn = e.target.closest('button[data-copy]');
        if (!btn) return;
        const text = btn.getAttribute('data-copy') || '';
        navigator.clipboard?.writeText(text);
        const label = btn.querySelector('span:last-of-type');
        if (label) {
            const old = label.textContent;
            label.textContent = 'Copied';
            btn.style.color = 'var(--color-sky-400)';
            setTimeout(() => {
                label.textContent = old;
                btn.style.color = '';
            }, 1200);
        }
    });

    // Auto-submit role-picker selects so role changes persist via SSR POST.
    document.addEventListener('change', function (e) {
        const sel = e.target.closest('.role-picker__select');
        if (!sel) return;
        const form = sel.closest('form');
        if (form) form.submit();
    });
})();
