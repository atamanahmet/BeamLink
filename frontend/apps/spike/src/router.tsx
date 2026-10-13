import { createHashRouter } from "react-router";
import { AppLayout } from "./layout/AppLayout.tsx";
import { HomeRoute } from "./routes/HomeRoute.tsx";
import { LoginRoute } from "./routes/LoginRoute.tsx";
import { ThemeRoute } from "./routes/ThemeRoute.tsx";

export const router = createHashRouter([
  {
    path: "/login",
    element: <LoginRoute />,
  },
  {
    path: "/",
    element: <AppLayout />,
    children: [
      {
        index: true,
        element: <HomeRoute />,
      },
      {
        path: "lazy",
        lazy: () => import("./routes/LazyRoute.tsx"),
      },
      {
        path: "drop",
        lazy: () => import("./routes/DropRoute.tsx"),
      },
      {
        path: "theme",
        element: <ThemeRoute />,
      },
    ],
  },
]);
