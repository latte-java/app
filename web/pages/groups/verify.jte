@import org.lattejava.app.model.Group
@import org.lattejava.app.model.GroupState
@import org.lattejava.app.model.view.GroupView
@import org.lattejava.app.model.view.VerificationView
@import org.lattejava.app.service.GroupKind
@import org.lattejava.app.service.validation.GroupValidator
@param GroupView groupView
@param VerificationView verification
@param String status
@param boolean githubLinked

!{Group group = groupView.group();}

@template.layout.main(view=groupView.mainView(), pageTitle="Verify " + group.name(), activeNav="groups", activeGroupId=group.name(), content=@`
  <nav class="flex items-center gap-1.5 mb-3.5 text-sm font-medium text-slate-500">
    <a class="hover:text-slate-900 dark:hover:text-slate-100 no-underline" href="/app/groups/">Groups</a>
    @template.components.icon(name = "chevron-right", size = 11)
    <a class="hover:text-slate-900 dark:hover:text-slate-100 no-underline"
       href="/app/groups/${group.name()}/">@template.components.italic-name(text = group.name(), size = "text-sm")</a>
    @template.components.icon(name = "chevron-right", size = 11)
    <span>Verify</span>
  </nav>
  @if(GroupValidator.kindOf(group.name()) == GroupKind.REVERSE_DNS_GITHUB)
    @template.components.page-header(
    title = "Verify " + group.name(),
    subtitle = "We'll check that your linked GitHub account owns this name."
    )
    <div class="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg shadow-sm p-6">
      <p class="m-0 mb-4 text-sm text-slate-600 dark:text-slate-300">
        This group uses GitHub for verification. If you haven't already, connect your GitHub account first, then verify.
      </p>
      <div class="flex flex-wrap gap-2">
        @if(githubLinked)
          <form method="post" action="/app/groups/${group.name()}/verify/github" class="inline">
            @template.components.button(label = "Verify with GitHub", variant = "primary", icon = "check", type = "submit")
          </form>
        @endif
        @template.components.button(label = "Connect GitHub", variant = "secondary", icon = "github", href = "/app/oauth/github/connect?groupName=" + group.name())
      </div>
      @if("verified".equals(status))
        @template.components.admonition(content = @`Verified.`, tone = "success", cssClass = "mt-4")
      @elseif("not_linked".equals(status))
        @template.components.admonition(content = @`Click "Connect GitHub" to link your GitHub account, then try again.`, tone = "warn", cssClass = "mt-4")
      @elseif("unauthorized".equals(status))
        @template.components.admonition(content = @`Your GitHub link expired. Click "Connect GitHub" to re-link.`, tone = "warn", cssClass = "mt-4")
      @elseif("not_authorized".equals(status))
        @template.components.admonition(content = @`Your GitHub account isn't a member of the org or doesn't match the personal account in this group name.`, tone = "danger", cssClass = "mt-4")
      @elseif("oauth_failed".equals(status))
        @template.components.admonition(content = @`GitHub didn't authorize the connection. Try "Connect GitHub" again.`, tone = "danger", cssClass = "mt-4")
      @elseif("link_failed".equals(status))
        @template.components.admonition(content = @`The link with GitHub failed. You can try connecting again, but if the problem persists, open a GitHub issue with the Latte Project.`, tone = "danger", cssClass = "mt-4")
      @endif
    </div>
  @else
    @template.components.page-header(
    title = "Verify " + group.name(),
    subtitle = "Add a TXT record to your DNS so Latte can confirm you own the domain."
    )
    <form class="grid grid-cols-1 lg:grid-cols-5 gap-4 items-start" method="post" action="/app/groups/${group.name()}/verify/check">
      <div class="lg:col-span-3 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg shadow-sm p-6">
        <div class="flex gap-4 py-5 border-t border-slate-200 dark:border-slate-700 first:border-t-0 first:pt-0">
          <div class="inline-flex items-center justify-center w-7 h-7 rounded-full bg-sky-50 text-sky-600 dark:bg-sky-950/40 dark:text-sky-400 text-sm font-bold shrink-0">
            1
          </div>
          <div class="flex-1">
            <h3 class="m-0 mb-1.5 text-base font-bold text-slate-900 dark:text-slate-100">Open your DNS provider</h3>
            <p class="m-0 text-sm text-slate-600 dark:text-slate-300">
              Sign in to wherever you manage DNS for
              @template.components.italic-name(text = verification.domain(), size = "text-sm") —
              Cloudflare, Route53, Namecheap, etc.
            </p>
          </div>
        </div>
        <div class="flex gap-4 py-5 border-t border-slate-200 dark:border-slate-700">
          <div class="inline-flex items-center justify-center w-7 h-7 rounded-full bg-sky-50 text-sky-600 dark:bg-sky-950/40 dark:text-sky-400 text-sm font-bold shrink-0">
            2
          </div>
          <div class="flex-1">
            <h3 class="m-0 mb-1.5 text-base font-bold text-slate-900 dark:text-slate-100">Add this TXT record</h3>
            <p class="m-0 mb-3 text-sm text-slate-600 dark:text-slate-300">
              Use the values below exactly. Most providers propagate in a few minutes.
            </p>
            <div class="grid grid-cols-[90px_1fr] gap-y-2 gap-x-5 p-4 bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-md">
              <span class="text-[11px] font-semibold uppercase tracking-widest text-slate-500">Type</span>
              <span class="font-mono text-sm text-slate-900 dark:text-slate-100">TXT</span>
              <span class="text-[11px] font-semibold uppercase tracking-widest text-slate-500">Name</span>
              <span class="font-mono text-sm text-slate-900 dark:text-slate-100 break-all">${verification.recordName()}</span>
              <span class="text-[11px] font-semibold uppercase tracking-widest text-slate-500">Value</span>
              <span class="font-mono text-sm text-slate-900 dark:text-slate-100 break-all">${verification.recordValue()}</span>
              <span class="text-[11px] font-semibold uppercase tracking-widest text-slate-500">TTL</span>
              <span class="font-mono text-sm text-slate-900 dark:text-slate-100">300 (5 minutes)</span>
            </div>
          </div>
        </div>
        <div class="flex gap-4 py-5 border-t border-slate-200 dark:border-slate-700">
          <div class="inline-flex items-center justify-center w-7 h-7 rounded-full bg-sky-50 text-sky-600 dark:bg-sky-950/40 dark:text-sky-400 text-sm font-bold shrink-0">
            3
          </div>
          <div class="flex-1">
            <h3 class="m-0 mb-1.5 text-base font-bold text-slate-900 dark:text-slate-100">Check verification</h3>
            <p class="m-0 mb-3 text-sm text-slate-600 dark:text-slate-300">
              Latte will resolve<code>${verification.recordName()}</code> and look for the value above.
            </p>
            @if(verification.dnsRecordFound() && verification.valueMatches())
              @template.components.admonition(content = @`DNS record found and value matches. You're verified.`, tone = "success")
            @elseif(verification.dnsRecordFound())
              @template.components.admonition(content = @`Record found but the value doesn't match.`, tone = "danger")
            @elseif(group.state() == GroupState.FAILED)
              @template.components.admonition(content = @`The previous verification attempt expired. Confirm the TXT record above is in place, then click "Check now" to try again.`, tone = "danger")
            @else
              @template.components.admonition(content = @`Waiting on DNS. Last checked ${verification.lastCheckedAt().toString()}.`, tone = "neutral")
            @endif
            <div class="flex gap-2 mt-3.5">
              @template.components.button(label = "Check now", variant = "primary", icon = "refresh", type = "submit")
            </div>
          </div>
        </div>
      </div>
      <div class="lg:col-span-2 flex flex-col gap-4">
        <div class="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg shadow-sm p-6">
          <h3 class="m-0 mb-3 text-xs font-bold uppercase tracking-widest text-slate-900 dark:text-slate-100">Why verify?</h3>
          <p class="m-0 mb-2.5 text-sm text-slate-600 dark:text-slate-300 leading-relaxed">
            Reverse-domain groups must prove they control the matching domain. This prevents anyone from publishing under
            @template.components.italic-name(text = group.name(), size = "text-sm")
            unless they own
            @template.components.italic-name(text = verification.domain(), size = "text-sm").
          </p>
        </div>
      </div>
    </form>
  @endif
`)
