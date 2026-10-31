import type { ButtonHTMLAttributes } from 'react'
import './Button.css'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant: 'primary' | 'booking' | 'text'
}

function Button({ variant, className = '', ...props }: ButtonProps) {
  const classes = ['shared-button', `shared-button--${variant}`, className]
    .filter(Boolean)
    .join(' ')

  return <button className={classes} {...props} />
}

export default Button