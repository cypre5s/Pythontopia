namespace Pythontopia.Core.Dto;

/// <summary>POST /api/auth/login body.</summary>
public sealed record LoginRequest(string Username, string Password);

/// <summary>
/// POST /api/auth/register body. Username must match <c>[A-Za-z0-9_.-]{3,64}</c>,
/// password length 8-72, displayName &lt;= 128 chars, role is "STUDENT" for self sign-up.
/// </summary>
public sealed record RegisterRequest(
    string Username,
    string Password,
    string DisplayName,
    string Role = "STUDENT");

/// <summary>Authenticated user envelope. Role is one of STUDENT / TEACHER / ADMIN.</summary>
public sealed record UserDto(long Id, string Username, string? DisplayName, string Role);

/// <summary>POST /api/auth/login response. <see cref="ExpiresAt"/> is ISO-8601 (token valid ~12h).</summary>
public sealed record LoginResponse(string Token, DateTimeOffset ExpiresAt, UserDto User);
