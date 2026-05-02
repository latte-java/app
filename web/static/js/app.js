// Latte repository portal — minimal client JS.
// SSR is the default; this only adds copy-to-clipboard and form auto-submits.

(function () {
    // Copy buttons inside <code-block>
    document.addEventListener('click', function (e) {
        const btn = e.target.closest('.codeblock__copy');
        if (!btn) return;
        const text = btn.getAttribute('data-copy') || '';
        navigator.clipboard?.writeText(text);
        const span = btn.querySelector('span');
        if (span) {
            const old = span.textContent;
            span.textContent = 'copied';
            btn.style.color = 'var(--brand-cup)';
            setTimeout(() => {
                span.textContent = old;
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
