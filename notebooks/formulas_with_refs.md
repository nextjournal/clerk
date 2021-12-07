# Clerk Markdown with Mathjax Equation Refs

## Todo

- [ ] block formula margins
- [ ] equation references alignment
- [ ] avoid using `$$` for latex env equations

## Formulas

Cosine is injective for inputs within the half period $\bigoplus_\alpha$

$$
\begin{equation}
\label{eq:cosinjective}
\cos \theta_1 = \cos \theta_2 \implies \theta_1 = \theta_2
\end{equation}
$$

reference the above equation by $\eqref{eq:cosinjective}$ ahoi, block formulas should have a bigger top margin.

We can also give equations a custom name

$$
\begin{equation}
\int_0^\infty \frac{x^2}{e^x-1}\,dx = \frac{\pi^4}{15}
\label{eq:some-label}
\tag{Eq1}
\end{equation}
$$

and can reference this here $\eqref{eq:some-label}$ ok.
