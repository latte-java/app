<%--
    Verification status pill: Verified (green), Pending (amber), Failed (red).
--%>
@import org.lattejava.app.model.GroupState
@param GroupState status

@if(status == GroupState.VERIFIED)
  @template.components.badge(label = "Verified", tone = "success")
@elseif(status == GroupState.PENDING)
  @template.components.badge(label = "Pending", tone = "warn")
@elseif(status == GroupState.FAILED)
  @template.components.badge(label = "Failed", tone = "danger")
@endif
