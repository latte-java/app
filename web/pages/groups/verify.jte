@import org.lattejava.app.model.View
@import org.lattejava.app.model.Group
@import org.lattejava.app.model.VerificationChallenge
@import java.util.List
@param View view
@param Group group
@param List<Group> sidebarGroups
@param VerificationChallenge challenge

@template.layout.main(view = view, pageTitle = "Verify " + group.domain(), activeNav = "groups", activeGroupId = group.id(), sidebarGroups = sidebarGroups, content = @`
  <nav class="flex items-center gap-1.5 mb-3.5 text-sm font-medium text-slate-500">
    <a class="hover:text-slate-900 dark:hover:text-slate-100 no-underline" href="/groups">Groups</a>
    @template.components.icon(name = "chevron-right", size = 11)
    <a class="hover:text-slate-900 dark:hover:text-slate-100 no-underline"
       href="/groups/${group.id()}">@template.components.italic-name(text = group.name(), size = "text-sm")</a>
    @template.components.icon(name = "chevron-right", size = 11)
    <span>Verify</span>
  </nav>
  @template.components.page-header(
  title = "Verify " + group.domain(),
  subtitle = "Add a TXT record to your DNS so Latte can confirm you own the domain."
  )
  <form class="grid grid-cols-1 lg:grid-cols-5 gap-4 items-start" method="post"
        action="/groups/${group.id()}/verify/check">
    <div
        class="lg:col-span-3 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg shadow-sm p-6">
      <div class="flex gap-4 py-5 border-t border-slate-200 dark:border-slate-700 first:border-t-0 first:pt-0">
        <div
            class="inline-flex items-center justify-center w-7 h-7 rounded-full bg-sky-50 text-sky-600 dark:bg-sky-950/40 dark:text-sky-400 text-sm font-bold shrink-0">
          1
        </div>
        <div class="flex-1">
          <h3 class="m-0 mb-1.5 text-base font-bold text-slate-900 dark:text-slate-100">Open your DNS provider</h3>
          <p class="m-0 text-sm text-slate-600 dark:text-slate-300">Sign in to wherever you manage DNS
            for @template.components.italic-name(text = challenge.domain(), size = "text-sm") — Cloudflare, Route53,
            Namecheap, etc.</p>
        </div>
      </div>
      <div class="flex gap-4 py-5 border-t border-slate-200 dark:border-slate-700">
        <div
            class="inline-flex items-center justify-center w-7 h-7 rounded-full bg-sky-50 text-sky-600 dark:bg-sky-950/40 dark:text-sky-400 text-sm font-bold shrink-0">
          2
        </div>
        <div class="flex-1">
          <h3 class="m-0 mb-1.5 text-base font-bold text-slate-900 dark:text-slate-100">Add this TXT record</h3>
          <p class="m-0 mb-3 text-sm text-slate-600 dark:text-slate-300">Use the values below exactly. Most providers
            propagate in a few minutes.</p>
          <div
              class="grid gap-y-2 gap-x-5 p-4 bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-md"
              style="grid-template-columns: 90px 1fr;">
            <span class="text-[11px] font-semibold uppercase tracking-widest text-slate-500">Type</span>
            <span class="font-mono text-sm text-slate-900 dark:text-slate-100">TXT</span>
            <span class="text-[11px] font-semibold uppercase tracking-widest text-slate-500">Name</span>
            <span
                class="font-mono text-sm text-slate-900 dark:text-slate-100 break-all">${challenge.recordName()}</span>
            <span class="text-[11px] font-semibold uppercase tracking-widest text-slate-500">Value</span>
            <span
                class="font-mono text-sm text-slate-900 dark:text-slate-100 break-all">${challenge.recordValue()}</span>
            <span class="text-[11px] font-semibold uppercase tracking-widest text-slate-500">TTL</span>
            <span class="font-mono text-sm text-slate-900 dark:text-slate-100">300 (5 minutes)</span>
          </div>
        </div>
      </div>
      <div class="flex gap-4 py-5 border-t border-slate-200 dark:border-slate-700">
        <div
            class="inline-flex items-center justify-center w-7 h-7 rounded-full bg-sky-50 text-sky-600 dark:bg-sky-950/40 dark:text-sky-400 text-sm font-bold shrink-0">
          3
        </div>
        <div class="flex-1">
          <h3 class="m-0 mb-1.5 text-base font-bold text-slate-900 dark:text-slate-100">Check verification</h3>
          <p class="m-0 mb-3 text-sm text-slate-600 dark:text-slate-300">Latte will resolve
            <code>${challenge.recordName()}</code> and look for the value above.</p>
          @if(challenge.dnsRecordFound() && challenge.valueMatches())
            <div
                class="flex items-center gap-2.5 px-3.5 py-3 rounded-md bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-300 text-sm font-medium">
              @template.components.icon(name = "check", size = 14)
              DNS record found and value matches. You're verified.
            </div>
          @elseif(challenge.dnsRecordFound() && !challenge.valueMatches())
            <div
                class="flex items-center gap-2.5 px-3.5 py-3 rounded-md bg-red-50 dark:bg-red-950/40 text-red-700 dark:text-red-300 text-sm font-medium">
              @template.components.icon(name = "alert", size = 14)
              Record found but the value doesn't match.
            </div>
          @else
            <div
                class="flex items-center gap-2.5 px-3.5 py-3 rounded-md bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 text-sm font-medium">
              @template.components.icon(name = "clock", size = 14)
              Waiting on DNS. Last checked ${challenge.lastCheckedAt()}.
            </div>
          @endif
          <div class="flex gap-2 mt-3.5">
            @template.components.button(label = "Check now", variant = "primary", icon = "refresh", type = "submit")
            @template.components.button(label = "Cancel verification", variant = "ghost", href = "/groups/" + group.id())
          </div>
        </div>
      </div>
    </div>
    <div class="lg:col-span-2 flex flex-col gap-4">
      <div class="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg shadow-sm p-6">
        <h3 class="m-0 mb-3 text-xs font-bold uppercase tracking-widest text-slate-900 dark:text-slate-100">Why
          verify?</h3>
        <p class="m-0 mb-2.5 text-sm text-slate-600 dark:text-slate-300 leading-relaxed">Reverse-domain groups must
          prove they control the matching domain. This prevents anyone from publishing
          under @template.components.italic-name(text = group.name(), size = "text-sm") unless they
          own @template.components.italic-name(text = challenge.domain(), size = "text-sm").</p>
      </div>
      <div class="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg shadow-sm p-6">
        <h3 class="m-0 mb-3 text-xs font-bold uppercase tracking-widest text-slate-900 dark:text-slate-100">Or use the
          cli</h3>
        @template.components.code-block(command = "latte verify " + group.name())
      </div>
    </div>
  </form>
`)
