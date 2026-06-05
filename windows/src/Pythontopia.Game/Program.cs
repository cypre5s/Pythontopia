using Pythontopia.Core.Config;
using Pythontopia.Game;

// Entry point for the standalone pixel-town window. The WinUI TownPage launches
// this executable as its own process for the MVP (SwapChainPanel embedding is a
// documented follow-up). Backend wiring is optional and driven by environment:
//   PYT_BACKEND_URL  -> backend base URL (defaults to http://localhost:18080)
//   PYT_TOKEN        -> JWT bearer; when present the town pulls the real world
//                       snapshot + player position via POST /api/game/bootstrap.
var config = AppConfig.FromEnvironment();
var token = Environment.GetEnvironmentVariable("PYT_TOKEN");

using var game = new TownGame(config, string.IsNullOrWhiteSpace(token) ? null : token);
game.Run();
