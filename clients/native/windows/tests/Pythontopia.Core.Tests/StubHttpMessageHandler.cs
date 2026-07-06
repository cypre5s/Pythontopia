using System.Net;

namespace Pythontopia.Core.Tests;

/// <summary>
/// A test double for <see cref="HttpMessageHandler"/> that routes every request through a
/// caller-supplied delegate, so <c>ApiClient</c> can be exercised without a live backend.
/// </summary>
internal sealed class StubHttpMessageHandler : HttpMessageHandler
{
    private readonly Func<HttpRequestMessage, HttpResponseMessage> _responder;

    public StubHttpMessageHandler(Func<HttpRequestMessage, HttpResponseMessage> responder) =>
        _responder = responder ?? throw new ArgumentNullException(nameof(responder));

    public int CallCount { get; private set; }

    protected override Task<HttpResponseMessage> SendAsync(
        HttpRequestMessage request, CancellationToken cancellationToken)
    {
        CallCount++;
        cancellationToken.ThrowIfCancellationRequested();
        return Task.FromResult(_responder(request));
    }

    /// <summary>Builds a JSON response with the given status code.</summary>
    public static HttpResponseMessage Json(HttpStatusCode status, string body) =>
        new(status)
        {
            Content = new StringContent(body, System.Text.Encoding.UTF8, "application/json"),
        };
}
