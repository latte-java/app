<%--
    Avatar — Gravatar image keyed by email. Empty/null email falls back to a stable identicon.
--%>
@import org.lattejava.app.util.Gravatar
@param String email
@param int size = 32

<img src="${Gravatar.url(email, size)}"
     alt=""
     width="${size}"
     height="${size}"
     crossorigin="anonymous"
     referrerpolicy="no-referrer"
     class="rounded-full border border-slate-300 block"/>
