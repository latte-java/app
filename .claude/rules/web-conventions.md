# Web Conventions

## Project design

The web application is designed such that each component has a specific purposed and is placed in a specific package to organize the code. Here are the conventions that must be followed:

1. Controllers are placed in the `org.lattejava.app.controller` package. 
   1. Controllers are instantiated as needed in the Main.main() method.
   2. Routes and controller methods are initialized in the `Main.main()` method.
   3. Controller methods should be `public` in the Controller classes since they are referenced from `Main`.
   4. Follow existing structure in `Main.main()` and existing controller classes.
   5. Always prefer `Map.of()` over `new HashMap<>()` when passing parameters to a view. Only use `new HashMap<>()` if there are null values.
2. Services are placed in the `org.lattejava.app.service` package. New services need to be added to the `Services` class as well. They are initialized in `Services.initialize()` and are all singletons.
3. Validation is not done in the services. It is separated into separate classes that are placed in the `org.lattejava.app.service.validation` package. Services should call the validation as needed and throw a `ValidationException` if the validation fails.

## Path prefixes

When multiple routes share a path segment, group them with `Web.prefix(...)` rather than repeating the segment in each route declaration. Nested prefixes are allowed and encouraged for hierarchy.

### Examples

```java
// Correct
web.prefix("/app", app -> {
  app.install(oidc.authenticated());
  app.get("/dashboard", this::dashboard);
  app.prefix("/groups", groups -> {
    groups.get("/", this::groupsList);
    groups.get("/new", this::groupsNewForm);
    groups.get("/{name}", this::groupsDetail);
    groups.post("/new", this::groupsCreate);
  });
});

// Incorrect
web.prefix("/app", r -> {
  r.install(oidc.authenticated());
  r.get("/dashboard", this::dashboard);
  r.get("/groups", this::groupsList);
  r.get("/groups/new", this::groupsNewForm);
  r.get("/groups/{name}", this::groupsDetail);
  r.post("/groups/new", this::groupsCreate);
});
```

### Why

Hierarchy in the route DSL mirrors hierarchy in the URL. Adding a new route under an existing prefix is a one-liner inside the prefix block; a typo in the segment is impossible if the prefix is set once.

## Trailing slash on listing pages

Listing pages — pages that show a collection of items — use a trailing `/` in the URL. Detail pages, form pages, and POST endpoints do not.

| Path                              | Description                       | Trailing `/`? |
|-----------------------------------|-----------------------------------|---------------|
| `/app/groups/`                    | List of groups                    | yes           |
| `/app/groups/new`                 | New-group form                    | no            |
| `/app/groups/{name}`              | Group detail                      | no            |
| `/app/groups/{name}/verify`       | Verify form                       | no            |
| `/app/groups/{name}/verify/check` | Verify check (POST)               | no            |
| `/app/`                           | Dashboard (overview, not listing) | no            |

### Why

The trailing `/` makes the URL convey "this is a directory of items" to both users and tooling. Browsers, link checkers, and search engines treat `/groups/` as a collection root distinct from `/groups/new`. It also keeps relative URLs inside the listing page predictable: `<a href="new">` resolves to `/app/groups/new` only when the listing is at `/app/groups/`.

### Applies to

- Route declarations in `Main.java` (or wherever routes are registered).
- Hardcoded paths in JTE templates (`href = "/app/groups/"`, `action = "/app/groups/"`).
- Redirect targets in handler code (`res.sendRedirect("/app/groups/", 303)`).

## View-model naming

Models used SOLELY to populate a view (a JTE template) are named `<Use>View` where `<Use>` is either the purpose of the view or the page it's used on. They live in `org.lattejava.app.model` alongside domain models.

### Examples

| Model name         | Purpose                                                                 |
|--------------------|-------------------------------------------------------------------------|
| `MainView`         | Chrome bound on every page (viewer, sidebar groups, active nav, theme)  |
| `VerificationView` | Data shown on the group verify page (TXT record details + DNS state)    |

### Why

The `View` suffix marks a model as transport-only — it has no schema mapping, no validator, and no persistence. Naming it after the use makes it easy to grep for the templates and handlers that consume it, and makes it obvious that any logic on it (computed strings, formatted dates) belongs to the view layer.

### Does not apply to

- Domain models that happen to be rendered (`Group`, `Member`, `GroupVerification`) — these are persisted and have validation/services around them. They keep their domain names.
- Service classes that produce views (`ViewService`, `VerificationService.buildChallenge`) — these stay named after their service role, not their output.

## Model normalization

Models should be normalized in the Compact Constructor whenever possible.

### Examples

```java
// Correct
public record Group(String name) {
  public Group {
    name = name != null ? name.trim().toLowerCase() : null;
  }
}

// Incorrect
public record Group(String name) {
}
```

### Why

This prevents unnecessary boilerplate in the service, controller, and view layers. It also provides guarantees that the model is properly normalized no matter how it's constructed.

## JTE templates

Inside JTE templates, calls to components should be compact. Here are some examples:

### Examples

```jte
// Correct
@template.layout.sidebar-link(href="/app/", icon="home", label="Dashboard", active=activeNav.equals("dashboard"))

// Incorrect
@template.layout.sidebar-link(href = "/app/",      icon = "home",    label = "Dashboard", active = activeNav.equals("dashboard"))```
