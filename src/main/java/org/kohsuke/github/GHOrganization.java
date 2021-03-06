package org.kohsuke.github;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Kohsuke Kawaguchi
 */
public class GHOrganization extends GHPerson {
    /**
     * Creates a new repository.
     *
     * @return
     *      Newly created repository.
     */
    public GHRepository createRepository(String name, String description, String homepage, String team, boolean isPublic) throws IOException {
        // such API doesn't exist, so fall back to HTML scraping
        WebClient wc = root.createWebClient();
        HtmlPage pg = (HtmlPage)wc.getPage("https://github.com/organizations/"+login+"/repositories/new");
        HtmlForm f = pg.getForms().get(1);
        f.getInputByName("repository[name]").setValueAttribute(name);
        f.getInputByName("repository[description]").setValueAttribute(description);
        f.getInputByName("repository[homepage]").setValueAttribute(homepage);
        f.getSelectByName("team_id").getOptionByText(team).setSelected(true);
        f.submit(f.getButtonByCaption("Create Repository"));

        return getRepository(name);

//        GHRepository r = new Poster(root).withCredential()
//                .with("name", name).with("description", description).with("homepage", homepage)
//                .with("public", isPublic ? 1 : 0).to(root.getApiURL("/organizations/"+login+"/repos/create"), JsonRepository.class).repository;
//        r.root = root;
//        return r;
    }

    /**
     * Teams by their names.
     */
    public Map<String,GHTeam> getTeams() throws IOException {
        return root.retrieveWithAuth("/organizations/"+login+"/teams",JsonTeams.class).toMap(this);
    }

    public enum Permission { ADMIN, PUSH, PULL }

    /**
     * Creates a new team and assigns the repositories.
     */
    public GHTeam createTeam(String name, Permission p, Collection<GHRepository> repositories) throws IOException {
        Poster post = new Poster(root).withCredential().with("team[name]", name).with("team[permission]", p.name().toLowerCase());
        for (GHRepository r : repositories) {
            post.with("team[repo_names][]",r.getOwnerName()+'/'+r.getName());
        }
        return post.to("/organizations/"+login+"/teams",JsonTeam.class).wrap(this);
    }

    public GHTeam createTeam(String name, Permission p, GHRepository... repositories) throws IOException {
        return createTeam(name,p, Arrays.asList(repositories));
    }

    /**
     * List up repositories that has some open pull requests.
     */
    public List<GHRepository> getRepositoriesWithOpenPullRequests() throws IOException {
        WebClient wc = root.createWebClient();
        HtmlPage pg = (HtmlPage)wc.getPage("https://github.com/organizations/"+login+"/dashboard/pulls");
        List<GHRepository> r = new ArrayList<GHRepository>();
        for (HtmlAnchor e : pg.getElementById("js-issue-list").<HtmlAnchor>selectNodes(".//UL[@class='smallnav']/LI[not(@class='zeroed')]/A")) {
            String a = e.getHrefAttribute();
            String name = a.substring(a.lastIndexOf('/')+1);
            r.add(getRepository(name));
        }
        return r;
    }

    /**
     * Gets all the open pull requests in this organizataion.
     */
    public List<GHPullRequest> getPullRequests() throws IOException {
        List<GHPullRequest> all = new ArrayList<GHPullRequest>();
        for (GHRepository r : getRepositoriesWithOpenPullRequests()) {
            all.addAll(r.getPullRequests(GHIssueState.OPEN));
        }
        return all;
    }
}
