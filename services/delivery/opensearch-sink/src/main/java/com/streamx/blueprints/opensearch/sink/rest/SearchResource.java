package com.streamx.blueprints.opensearch.sink.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.streamx.blueprints.opensearch.sink.opensearch.DefaultRepository;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import java.util.Optional;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestResponse;

@Path("/search")
public class SearchResource {

  @Inject
  DefaultRepository defaultRepository;

  @Inject
  Logger log;

  @Inject
  SearchResultTransformer<JsonNode> resultTransformer;

  /**
   * Method to perform search queries.
   *
   * @return search result
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{searchTemplateId}")
  public Uni<RestResponse<JsonNode>> search(@PathParam("searchTemplateId") String searchTemplateId,
      @Context UriInfo ui) {
    var queryParameters = ui.getQueryParameters();
    var search = defaultRepository.searchByTemplate(searchTemplateId, queryParameters);

    return search.map(resultTransformer::transform)
        .map(RestResponse::ok)
        .onFailure().recoverWithItem(e -> {
          if (isSearchTemplateNotFound(e)) {
            log.infov("Search template with searchTemplateId '{0}' could not be found.",
                searchTemplateId);
            return RestResponse.notFound();
          }

          log.errorv(e, "Unknown exception occured for searchTemplateId: '{0}'.",
              searchTemplateId);
          return RestResponse.serverError();
        });
  }

  /**
   * Method to perform search queries with request body.
   *
   * @return search result
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{searchTemplateId}/body")
  public Uni<RestResponse<JsonNode>> searchByBody(
      @PathParam("searchTemplateId") String searchTemplateId,
      String body) {
    var search = defaultRepository.searchByTemplate(searchTemplateId, body);
    return search.map(resultTransformer::transform)
        .map(RestResponse::ok)
        .onFailure().recoverWithItem(e -> {
          if (isSearchTemplateNotFound(e)) {
            log.infov("Search template with searchTemplateId '{0}' could not be found.",
                searchTemplateId);
            return RestResponse.notFound();
          }
          log.errorv(e, "Unknown exception occured for searchTemplateId: '{0}'.",
              searchTemplateId);
          return RestResponse.serverError();
        });
  }

  private boolean isSearchTemplateNotFound(Throwable e) {
    return e instanceof ResponseException re
        && Optional.of(re)
        .map(ResponseException::getResponse)
        .map(Response::getStatusLine)
        .stream().anyMatch(sl -> sl.getStatusCode() == 404);
  }
}
