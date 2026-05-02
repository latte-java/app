<%--
    Avatar — initials in a colored circle.
    Hue is deterministic from initials; uses Tailwind palette colors.
--%>
@param String initials
@param int size = 32

!{String[] palette = {"bg-rose-600","bg-orange-600","bg-amber-600","bg-emerald-600","bg-sky-600","bg-indigo-600","bg-violet-600","bg-fuchsia-600"};}
!{int h = 0;}
!{for (int i = 0; i < initials.length(); i++) { h = (h * 31 + initials.charAt(i)) & 0xfff; }}
!{String bg = palette[h % palette.length];}
!{String s = String.valueOf(size);}

<span class="inline-flex items-center justify-center rounded-full text-white font-semibold ${bg}"
      style="width:${s}px;height:${s}px;font-size:${String.valueOf((int)(size * 0.42))}px;line-height:1;">
    ${initials}
</span>
