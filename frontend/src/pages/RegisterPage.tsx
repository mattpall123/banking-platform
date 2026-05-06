// src/pages/RegisterPage.tsx

import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "@/auth/useAuth";
import { ApiError } from "@/api/client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import type { RegisterRequest } from "@/api/auth";

const DEFAULTS: RegisterRequest = {
  email: "",
  password: "",
  legalFirstName: "",
  legalLastName: "",
  dateOfBirth: "1995-06-15",
  phone: "+14165555555",
  streetAddress: "789 King Street",
  city: "Toronto",
  province: "ON",
  postalCode: "M5H1A1",
  occupation: "Engineer",
  idType: "DRIVERS_LICENCE",
  idNumberLast4: "1234",
  idExpiryDate: "2030-01-01",
  pep: false,
};

export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState<RegisterRequest>(DEFAULTS);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function setField<K extends keyof RegisterRequest>(key: K, value: RegisterRequest[K]) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await register(form);
      navigate("/", { replace: true });
    } catch (err) {
      if (err instanceof ApiError) {
        const body = err.body as { message?: string } | null;
        setError(body?.message ?? "Registration failed.");
      } else {
        setError("Something went wrong. Please try again.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-background p-4">
      <Card className="w-full max-w-2xl">
        <CardHeader>
          <CardTitle>Create your account</CardTitle>
          <CardDescription>
            We collect KYC info to comply with Canadian banking regulations (FINTRAC).
            Some fields are pre-filled to make demoing easier.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={onSubmit} className="space-y-4">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <Field id="email" label="Email" type="email"
                     value={form.email} onChange={(v) => setField("email", v)} />
              <Field id="password" label="Password" type="password"
                     value={form.password} onChange={(v) => setField("password", v)} />
              <Field id="legalFirstName" label="First name"
                     value={form.legalFirstName} onChange={(v) => setField("legalFirstName", v)} />
              <Field id="legalLastName" label="Last name"
                     value={form.legalLastName} onChange={(v) => setField("legalLastName", v)} />
              <Field id="dateOfBirth" label="Date of birth" type="date"
                     value={form.dateOfBirth} onChange={(v) => setField("dateOfBirth", v)} />
              <Field id="phone" label="Phone"
                     value={form.phone} onChange={(v) => setField("phone", v)} />
              <Field id="streetAddress" label="Street address"
                     value={form.streetAddress} onChange={(v) => setField("streetAddress", v)} />
              <Field id="city" label="City"
                     value={form.city} onChange={(v) => setField("city", v)} />
              <Field id="province" label="Province"
                     value={form.province} onChange={(v) => setField("province", v)} />
              <Field id="postalCode" label="Postal code"
                     value={form.postalCode} onChange={(v) => setField("postalCode", v)} />
              <Field id="occupation" label="Occupation"
                     value={form.occupation} onChange={(v) => setField("occupation", v)} />
              <Field id="idNumberLast4" label="ID last 4 digits"
                     value={form.idNumberLast4} onChange={(v) => setField("idNumberLast4", v)} />
            </div>
            {error && <p className="text-sm text-destructive">{error}</p>}
            <Button type="submit" className="w-full" disabled={submitting}>
              {submitting ? "Creating account…" : "Create account"}
            </Button>
            <p className="text-sm text-muted-foreground text-center">
              Already have an account?{" "}
              <Link to="/login" className="underline hover:text-foreground">
                Sign in
              </Link>
            </p>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}

function Field({
  id, label, type = "text", value, onChange,
}: {
  id: string; label: string; type?: string; value: string;
  onChange: (v: string) => void;
}) {
  return (
    <div className="space-y-2">
      <Label htmlFor={id}>{label}</Label>
      <Input id={id} type={type} value={value} required
             onChange={(e) => onChange(e.target.value)} />
    </div>
  );
}