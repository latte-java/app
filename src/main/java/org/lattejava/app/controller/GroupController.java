package org.lattejava.app.controller;

import module java.base;
import module org.lattejava.app;
import module org.lattejava.http;
import module org.lattejava.web;

import org.lattejava.app.model.Member;

/**
 * A controller for groups.
 *
 * @author Brian Pontarelli
 */
public class GroupController {
  public static final String NAME = "groupName";
  private final GroupService groupService;
  private final MembershipService membershipService;
  private final OIDC<User> oidc;
  private final JTETemplates templates;
  private final VerificationService verificationService;
  private final ViewService viewService;

  public GroupController(OIDC<User> oidc, JTETemplates templates) {
    this.oidc = oidc;
    this.templates = templates;
    this.groupService = Services.groupService();
    this.membershipService = Services.membershipService();
    this.verificationService = Services.verificationService();
    this.viewService = Services.viewService();
  }

  public void checkVerification(HTTPRequest req, HTTPResponse res) {
    String groupName = (String) req.getAttribute(NAME);
    Optional<Group> groupOpt = groupService.findGroup(groupName);
    if (groupOpt.isPresent() && groupOpt.get().state() == GroupState.VERIFIED) {
      res.sendRedirect("/app/groups/" + groupName + "/", 303);
      return;
    }

    verificationService.check(groupName, Instant.now());
    res.sendRedirect("/app/groups/" + groupName + "/", 303);
  }

  public void create(HTTPRequest req, HTTPResponse res) throws IOException {
    User user = oidc.user();
    String name = req.getParameter("name");
    String description = req.getParameter("description");
    Group input = new Group(
        name,
        description == null ? "" : description,
        GroupState.PENDING,
        null,
        Instant.EPOCH,
        null
    );

    try {
      groupService.create(input, user);
      res.sendRedirect("/app/groups/" + name + "/", 303);
    } catch (ValidationException e) {
      MainView view = viewService.buildMainView(user);
      templates.html("pages/groups/new.jte", req, res,
          Map.of(
              "view", view,
              "name", name == null ? "" : name,
              "description", description == null ? "" : description,
              "errors", e.errors()
          )
      );
    }
  }

  public void delete(HTTPRequest req, HTTPResponse res) {
    String groupName = (String) req.getAttribute(NAME);
    User current = oidc.user();
    Optional<Group> groupOpt = groupService.findGroup(groupName);
    if (groupOpt.isEmpty()) {
      res.setStatus(404);
      return;
    }

    try {
      groupService.delete(groupOpt.get(), current);
      res.sendRedirect("/app/groups/", 303);
    } catch (ValidationException e) {
      res.sendRedirect("/app/groups/" + groupName + "/settings", 303);
    }
  }

  public void detail(HTTPRequest req, HTTPResponse res) throws IOException {
    User user = oidc.user();
    Group group = findGroup(req);
    Optional<Member> viewerMembership = membershipService.findMember(group.name(), user.userId());
    MainView view = viewService.buildMainView(user);
    Map<String, Object> params = new HashMap<>();
    params.put("activeTab", "overview");
    params.put("group", group);
    params.put("view", view);
    params.put("viewerMembership", viewerMembership.orElse(null));
    templates.html("pages/groups/detail.jte", req, res, params);
  }

  public Group findGroup(HTTPRequest req) {
    String groupName = (String) req.getAttribute(NAME);
    Optional<Group> groupOpt = groupService.findGroup(groupName);
    if (groupOpt.isEmpty()) {
      throw new MissingException();
    }

    return groupOpt.get();
  }

  public void list(HTTPRequest req, HTTPResponse res) throws IOException {
    User user = oidc.user();
    String filter = req.getURLParameter("q");
    MainView view = viewService.buildMainView(user);
    List<Group> groups = groupService.listForUser(user, filter);
    templates.html("pages/groups/list.jte", req, res,
        Map.of(
            "view", view,
            "groups", groups,
            "filter", filter == null ? "" : filter
        )
    );
  }

  public void newForm(HTTPRequest req, HTTPResponse res) throws IOException {
    User user = oidc.user();
    MainView view = viewService.buildMainView(user);
    templates.html("pages/groups/new.jte", req, res,
        Map.of(
            "view", view,
            "name", "",
            "description", "",
            "errors", new Errors()
        )
    );
  }

  public void settings(HTTPRequest req, HTTPResponse res) throws IOException {
    User user = oidc.user();
    Group group = findGroup(req);
    MainView view = viewService.buildMainView(user);
    templates.html("pages/groups/detail.jte", req, res,
        Map.of(
            "activeTab", "settings",
            "group", group,
            "view", view
        )
    );
  }

  public void updateSettings(HTTPRequest req, HTTPResponse res) throws IOException {
    User user = oidc.user();
    Group group = findGroup(req);
    String description = req.getParameter("description");
    try {
      groupService.updateDescription(group, description);
      res.sendRedirect("/app/groups/" + group.name() + "/", 303);
    } catch (ValidationException e) {
      MainView view = viewService.buildMainView(user);
      Group submitted = new Group(group.name(), description, group.state(), group.verificationCode(),
          group.createdAt(), group.verifiedAt());
      templates.html("pages/groups/detail.jte", req, res,
          Map.of(
              "activeTab", "settings",
              "group", submitted,
              "view", view,
              "errors", e.errors()
          )
      );
    }
  }

  public void verifyForm(HTTPRequest req, HTTPResponse res) throws IOException {
    Group group = findGroup(req);
    if (group.state() == GroupState.VERIFIED) {
      res.sendRedirect("/app/groups/" + group.name() + "/", 303);
      return;
    }

    Optional<GroupVerification> verOpt = verificationService.findVerification(group.name());
    VerificationView verification = VerificationView.buildView(group, verOpt.orElse(null));
    User user = oidc.user();
    MainView view = viewService.buildMainView(user);
    String status = req.getURLParameter("status");
    boolean githubLinked = GroupValidator.kindOf(group.name()) == GroupKind.REVERSE_DNS_GITHUB
        && verificationService.hasGitHubLink(user.userId());
    Map<String, Object> params = new HashMap<>();
    params.put("view", view);
    params.put("group", group);
    params.put("verification", verification);
    params.put("status", status);
    params.put("githubLinked", githubLinked);
    templates.html("pages/groups/verify.jte", req, res, params);
  }

  public void verifyGitHub(HTTPRequest req, HTTPResponse res) {
    String groupName = (String) req.getAttribute(NAME);
    Optional<Group> groupOpt = groupService.findGroup(groupName);
    if (groupOpt.isEmpty()) {
      res.setStatus(404);
      return;
    }

    User current = oidc.user();
    GitHubVerifyResult result;
    try {
      result = verificationService.verifyGitHub(groupOpt.get(), current);
    } catch (ValidationException e) {
      res.sendRedirect("/app/groups/" + groupName + "/verify", 303);
      return;
    }

    res.sendRedirect("/app/groups/" + groupName + "/?status=" + result.name().toLowerCase(), 303);
  }
}
