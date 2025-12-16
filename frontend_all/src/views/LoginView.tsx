import type { FormEvent } from "react";

type AuthMode = "signin" | "signup";

type AuthFormState = {
  username: string;
  password: string;
  status: "idle" | "loading" | "success" | "error";
  message: string | null;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onUsernameChange: (value: string) => void;
  onPasswordChange: (value: string) => void;
};

type LoginViewProps = {
  mode: AuthMode;
  onModeChange: (mode: AuthMode) => void;
  signin: AuthFormState;
  signup: AuthFormState;
  defaultCredentials: {
    username: string;
    password: string;
  };
};

export function LoginView({
  mode,
  onModeChange,
  signin,
  signup,
  defaultCredentials
}: LoginViewProps) {
  const active = mode === "signup" ? signup : signin;
  const isSignup = mode === "signup";
  const isLoading = active.status === "loading";
  const isError = active.status === "error";
  const isSuccess = active.status === "success";

  const headline = isSignup ? "Create your store account" : "Store Platform Sign In";
  const subtext = isSignup
    ? "Register a new account. You will be signed in automatically once registration completes."
    : "Sign in to manage products, carts, and customer orders.";

  return (
    <div className="auth-layout">
      <div className="auth-card">
        <header>
          <h1>{headline}</h1>
          <p>{subtext}</p>
        </header>

        <div className="auth-tabs">
          <button
            type="button"
            className={`auth-tab ${mode === "signin" ? "auth-tab-active" : ""}`}
            onClick={() => onModeChange("signin")}
            disabled={signin.status === "loading"}
          >
            Sign in
          </button>
          <button
            type="button"
            className={`auth-tab ${mode === "signup" ? "auth-tab-active" : ""}`}
            onClick={() => onModeChange("signup")}
            disabled={signup.status === "loading"}
          >
            Sign up
          </button>
        </div>

        <form onSubmit={active.onSubmit} className="auth-form">
          <label>
            Username
            <input
              value={active.username}
              onChange={(event) => active.onUsernameChange(event.target.value)}
              autoComplete="username"
              placeholder="your-username"
              disabled={isLoading}
            />
          </label>

          <label>
            Password
            <input
              type="password"
              value={active.password}
              onChange={(event) => active.onPasswordChange(event.target.value)}
              autoComplete={isSignup ? "new-password" : "current-password"}
              placeholder="••••••••"
              disabled={isLoading}
            />
          </label>

          <button type="submit" disabled={isLoading}>
            {isLoading ? "Submitting…" : isSignup ? "Create account" : "Sign in"}
          </button>
        </form>

        {active.message && (
          <p
            className={`feedback ${
              isError ? "feedback-error" : isSuccess ? "feedback-success" : ""
            }`}
          >
            {active.message}
          </p>
        )}

        <div className="auth-helper-card">
          <h2>Default credentials</h2>
          <p>
            A demo account is pre-provisioned for quick testing. Use these values on the sign-in tab
            if you do not wish to register right now:
          </p>
          <code>Username: {defaultCredentials.username}</code>
          <code>Password: {defaultCredentials.password}</code>
        </div>
      </div>
    </div>
  );
}

