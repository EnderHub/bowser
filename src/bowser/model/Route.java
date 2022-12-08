package bowser.model;

import static com.google.common.base.Preconditions.checkNotNull;
import static ox.util.Utils.propagate;

import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import bowser.template.Data;
import bowser.template.Template;

import ox.IO;
import ox.Json;
import ox.Log;
import ox.util.Regex;
import ox.x.XList;

public class Route {
  private static final String charMatcher = "[0-9a-zA-Z\\-_:@\\. ']*";

  public final Controller controller;
  public final String method, path;
  private boolean enableCaching;

  public final Pattern regex;
  private XList<String> namedGroups = null;

  public String resource;
  public Handler handler;
  public Data data = context -> {
  };

  private Template template;
  public byte[] resourceData;

  public String host = "";
  public boolean renderHead = true;

  public Route(Controller controller, String method, String path, boolean enableCaching) {
    this.controller = controller;
    this.method = method;
    this.path = path;
    this.enableCaching = enableCaching;

    if (path.contains("**")) {
      path = path.replace("**", ".*");
    } else {
      path = path.replace("*", charMatcher);
    }

    if (path.contains("{")) {
      namedGroups = XList.createWithCapacity(2);
      path = Regex.replaceAll("\\{(.*?)\\}", path, matcher -> {
        String paramName = matcher.group(1);
        namedGroups.add(paramName);
        return "(?<" + paramName + ">" + charMatcher + ")";
      });
    }

    path += "/?";
    regex = Pattern.compile(path);
  }

  public boolean matches(Request request) {
    if (!request.getMethod().equalsIgnoreCase(method)) {
      return false;
    }
    Matcher matcher = regex.matcher(request.path);
    if (!matcher.matches()) {
      return false;
    }
    String h = request.getHost();
    if (!host.isEmpty() && (h == null || !h.equals(host))) {
      return false;
    }

    for (int i = 1; i <= matcher.groupCount(); i++) {
      request.getJson().with(namedGroups.get(i - 1), matcher.group(i));
    }

    return true;
  }

  public Template getTemplate() {
    if ((this.template == null || !enableCaching) && resource != null && resource.endsWith(".html")) {
      try {
        URL url = controller.getResource(resource);
        checkNotNull(url, this + ": Could not find resource: " + resource);
        String source = IO.from(url).toString();
        this.template = Template.compile(source, controller, false, false, renderHead);
      } catch (Exception e) {
        Log.error("Problem compiling template: " + resource);
        throw propagate(e);
      }
    }
    return this.template;
  }

  public Route data(Data data) {
    this.data = data;
    return this;
  }

  public Route to(String resource) {
    this.resource = resource;
    return this;
  }

  public void load() {
    if (resource != null) {
      if (resource.endsWith(".html")) {
        getTemplate(); // warm the cache
      } else {
        resourceData = IO.from(controller.getClass(), resource).toByteArray();
      }
    }
  }

  public Route host(String host) {
    this.host = host;
    return this;
  }

  public Route to(Handler handler) {
    this.handler = handler;
    return this;
  }

  public Route headless() {
    renderHead = false;
    return this;
  }

  @Override
  public String toString() {
    return method + " " + path;
  }

  public Json toJson() {
    return Json.object()
        .with("method", this.method)
        .with("path", this.path)
        .with("controller", this.controller.getClass().getSimpleName() + ".java")
        .with("resource", this.resource);
  }
}
