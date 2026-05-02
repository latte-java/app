<%--
    Verified mark — small icon next to a group name.
--%>
@import org.lattejava.app.model.VerificationStatus
@param VerificationStatus status
@param int size = 14

@if(status == VerificationStatus.VERIFIED)
  <span class="inline-flex items-center text-sky-500" title="Verified">
        @template.components.icon(name = "check-circle", size = size)
    </span>
@elseif(status == VerificationStatus.PENDING)
  <span class="inline-flex items-center text-amber-500" title="Verification pending">
        @template.components.icon(name = "clock", size = size)
    </span>
@endif
