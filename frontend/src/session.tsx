import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";
import { createApi, type Api } from "./api/client.ts";
import type { User } from "./api/schemas.ts";

const TOKEN_KEY = "courtbook.token";
const USER_KEY = "courtbook.user";

type Session = {
  api: Api;
  user: User | null;
  login(email: string, password: string): Promise<void>;
  register(email: string, name: string, password: string): Promise<void>;
  logout(): void;
};

const SessionContext = createContext<Session | null>(null);

function read(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function write(key: string, value: string | null) {
  try {
    if (value === null) localStorage.removeItem(key);
    else localStorage.setItem(key, value);
  } catch {
    // storage blocked (private mode); the session just won't survive a reload
  }
}

export function SessionProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => read(TOKEN_KEY));
  const [user, setUser] = useState<User | null>(() => {
    const raw = read(USER_KEY);
    return raw ? (JSON.parse(raw) as User) : null;
  });

  const logout = useCallback(() => {
    write(TOKEN_KEY, null);
    write(USER_KEY, null);
    setToken(null);
    setUser(null);
  }, []);

  const api = useMemo(
    () =>
      createApi(
        import.meta.env.VITE_API_URL ?? "/api",
        { get: () => token, clear: () => write(TOKEN_KEY, null) },
        logout,
      ),
    [token, logout],
  );

  const login = useCallback(
    async (email: string, password: string) => {
      const res = await api.login(email, password);
      write(TOKEN_KEY, res.accessToken);
      write(USER_KEY, JSON.stringify(res.user));
      setToken(res.accessToken);
      setUser(res.user);
    },
    [api],
  );

  const register = useCallback(
    async (email: string, name: string, password: string) => {
      await api.register(email, name, password);
      await login(email, password);
    },
    [api, login],
  );

  const value = useMemo(() => ({ api, user: token ? user : null, login, register, logout }), [api, user, token, login, register, logout]);
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): Session {
  const s = useContext(SessionContext);
  if (!s) throw new Error("useSession outside SessionProvider");
  return s;
}

/** The logged-in user. Only call this on pages that require login. */
export function useUser(): User {
  const { user } = useSession();
  if (!user) throw new Error("not logged in");
  return user;
}
